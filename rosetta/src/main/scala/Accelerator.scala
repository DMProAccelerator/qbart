package rosetta

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.streams._


class TestDMAThresholder() extends RosettaAccelerator {
  val numMemPorts = 2
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val start         = Bool(INPUT)
    val baseAddrRead  = UInt(INPUT, width = 64)
    val baseAddrWrite = UInt(INPUT, width = 64)
    val byteCount     = UInt(INPUT, width = 32)
    val elemCount     = UInt(INPUT, width = 32)
    val threshCount   = UInt(INPUT, width = 32)
    val sum           = UInt(OUTPUT, width = 32)
    val cc            = UInt(OUTPUT, width = 32)
    val finished      = Bool(OUTPUT)
  }

  val rCC = Reg(init = UInt(0, 32))

  val reader = Module(new StreamReader(new StreamReaderParams(
    streamWidth = 64,
    fifoElems = 8,
    mem = PYNQParams.toMemReqParams(),
    maxBeats = 1,
    chanID = 0,
    disableThrottle = true
  ))).io
  val writer = Module(new StreamWriter(new StreamWriterParams(
    streamWidth = 64,
    mem = PYNQParams.toMemReqParams(),
    chanID = 0
  ))).io
  val handler = Module(new DMAHandler(64, PYNQParams)).io

  handler.start := io.start
  handler.byteCount := io.byteCount
  handler.elemCount := io.elemCount
  handler.threshCount := io.threshCount
  handler.baseAddrRead := io.baseAddrRead
  handler.baseAddrWrite := io.baseAddrWrite

  io.signature := makeDefaultSignature()
  io.finished := handler.finished

  // Read from port 0.
  reader <> handler.reader
  reader.req <> io.memPort(0).memRdReq
  reader.rsp <> io.memPort(0).memRdRsp
  plugMemWritePort(0)

  // Write to port 1.
  writer <> handler.writer
  writer.req <> io.memPort(1).memWrReq
  writer.rsp <> io.memPort(1).memWrRsp
  writer.wdat <> io.memPort(1).memWrDat
  plugMemReadPort(1)

  io.cc := rCC
  when (!io.start) {
    rCC := UInt(0)
  }
  .elsewhen (io.start & !io.finished) {
    rCC := rCC + UInt(1)
  }
}


class DMAHandler(w: Int, p: PlatformWrapperParams) extends Module {
  val io = new Bundle {
    val start         = Bool(INPUT)
    val byteCount     = UInt(INPUT, width = 32)
    val elemCount     = UInt(INPUT, width = 32)
    val threshCount   = UInt(INPUT, width = 32)
    val baseAddrRead  = UInt(INPUT, width = 64)
    val baseAddrWrite = UInt(INPUT, width = 64)
    val finished      = Bool(OUTPUT)
    var reader        = new StreamReaderIF(w, p.toMemReqParams).flip()
    var writer        = new StreamWriterIF(w, p.toMemReqParams).flip()
  }

  val thresholder = Module(new Thresholder(w)).io

  val bytesPerElem = w / 8

  val sIdle :: sReadThreshold :: sReadMatrix :: sApplyThreshold :: sReaderFlush :: sFinished :: Nil = Enum(UInt(), 6)

  val rState = Reg(init = UInt(sIdle))
  val rThresholds = Vec.fill(20) { Reg(init = UInt(0, width = w)) }
  val rIndex = Reg(init = UInt(0, 32))
  val rMatrixElem = Reg(init = UInt(0, width = w))
  val rMatrixValid = Reg(init = Bool(false))
  val rThresholdStart = Reg(init = Bool(false))
  val rBytesLeft = Reg(init = UInt(0, 32))

  io.reader.baseAddr := io.baseAddrRead
  io.reader.byteCount := io.byteCount
  io.writer.baseAddr := io.baseAddrWrite
  io.writer.byteCount := io.byteCount
  io.reader.out.ready := Bool(false)
  io.finished := Bool(false)
  io.writer.start := Bool(false)
  io.reader.start := Bool(false)

  thresholder.count <> io.writer.in
  thresholder.matrix.bits := rMatrixElem
  thresholder.matrix.valid := rMatrixValid
  thresholder.start := rThresholdStart
  thresholder.size := io.threshCount

  for (i <- 0 to rThresholds.size - 1) {
    thresholder.threshold(i) := rThresholds(i)
  }

  switch (rState) {
    is (sIdle) {
      rIndex := UInt(0)
      when (io.start) {
        rState := sReadThreshold
        rBytesLeft := io.byteCount
        io.reader.start := Bool(true)
      }
    }
    is (sReadThreshold) {
      when (rIndex === UInt(io.threshCount)) {
        rState := sReadMatrix
        io.writer.start := Bool(true)
      }
      .otherwise {
        io.reader.out.ready := Bool(true)
        when (io.reader.out.valid) {
          rThresholds(rIndex) := io.reader.out.bits
          rIndex := rIndex + UInt(1)
          rBytesLeft := rBytesLeft - UInt(bytesPerElem)
        }
      }
    }
    is (sReadMatrix) {
      when (rIndex === UInt(io.elemCount)) {
        rState := sReaderFlush
      }
      .otherwise {
        io.reader.out.ready := Bool(true)
        when (io.reader.out.valid) {
          rState := sApplyThreshold
          rIndex := rIndex + UInt(1)
          rMatrixValid := Bool(true)
          rThresholdStart := Bool(true)
          rMatrixElem := io.reader.out.bits
          rBytesLeft := rBytesLeft - UInt(bytesPerElem)
        }
      }
    }
    is (sApplyThreshold) {
      when (thresholder.count.valid) {
        when (io.writer.in.ready) {
          rState := sReadMatrix
          rMatrixValid := Bool(false)
          rThresholdStart := Bool(false)
        }
      }
    }
    is (sReaderFlush) {
      when (rBytesLeft === UInt(0)) {
        rState := sFinished
      }
      .otherwise {
        io.reader.out.ready := Bool(true)
        when (io.reader.out.valid) {
            rBytesLeft := rBytesLeft - UInt(bytesPerElem)
        }
      }
    }
    is (sFinished) {
      io.finished := Bool(true)
      when (!io.start) {
        rState := sIdle
      }
    }
  }
}


class Thresholder(w: Int) extends Module {
  val io = new Bundle {
    val start     = Bool(INPUT)
    val size      = UInt(INPUT, width = 32)
    val threshold = Vec.fill(255) { UInt(INPUT, width = w) }
    val matrix    = Decoupled(UInt(INPUT, width = w)).flip()
    val count     = Decoupled(UInt(OUTPUT, width = 32))
    val finished  = Bool(OUTPUT)
  }

  val sIdle :: sRunning :: sFinished :: Nil = Enum(UInt(), 3)

  val rState = Reg(init = sIdle)
  val rCount = Reg(init = UInt(0, 32))
  val rIndex = Reg(init = UInt(0, 32))

  io.finished := Bool(false)
  io.matrix.ready := Bool(false)
  io.count.valid := Bool(false)
  io.count.bits := rCount

  switch (rState) {
    is (sIdle) {
      rCount := UInt(0)
      rIndex := UInt(0)
      when (io.start) {
        rState := sRunning
      }
    }
    is (sRunning) {
      when (rIndex === io.size) {
        rState := sFinished
      }
      .elsewhen (io.matrix.valid && io.count.ready) { // REMOVE THIS?
        rCount := rCount + (UInt(io.matrix.bits) >= UInt(io.threshold(rIndex)))
        rIndex := rIndex + UInt(1)
      }
    }
    is (sFinished) {
      io.finished := Bool(true)
      when (!io.start) {
        rState := sIdle
      }
      .otherwise {
        io.count.valid := Bool(true)
        io.matrix.ready := Bool(true)
      }
    }
  }
}
