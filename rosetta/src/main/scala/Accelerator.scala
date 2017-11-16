package rosetta

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.streams._


class TestDMAThresholder() extends RosettaAccelerator {
  val numMemPorts = 2
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val baseAddrRead = UInt(INPUT, width = 64)
    val baseAddrWrite = UInt(INPUT, width = 64)
    val byteCount = UInt(INPUT, width = 32)
    val elemCount = UInt(INPUT, width = 32)
    val threshCount = UInt(INPUT, width = 32)
    val sum = UInt(OUTPUT, width = 32)
    val cc = UInt(OUTPUT, width = 32)
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
  val handler = Module(new DMAHandler(64)).io

  reader.start := handler.readerStart
  reader.baseAddr := io.baseAddrRead
  reader.byteCount := io.byteCount

  writer.start := handler.writerStart
  writer.baseAddr := io.baseAddrWrite
  writer.byteCount := io.byteCount

  handler.start := io.start
  handler.elemCount := io.elemCount
  handler.threshCount := io.threshCount

  io.signature := makeDefaultSignature()
  io.finished := handler.finished

  // Enable read to port 0.
  reader.out <> handler.in
  reader.req <> io.memPort(0).memRdReq
  io.memPort(0).memRdRsp <> reader.rsp
  plugMemWritePort(0)

  // Enable write to port 1.
  handler.out <> writer.in
  writer.req <> io.memPort(1).memWrReq
  writer.wdat <> io.memPort(1).memWrDat
  io.memPort(1).memWrRsp <> writer.rsp
  plugMemReadPort(1)

  io.cc := rCC
  when (!io.start) {
    rCC := UInt(0)
  }
  .elsewhen (io.start & !io.finished) {
    rCC := rCC + UInt(1)
  }
}


class DMAHandler(w: Int) extends Module {
  val io = new Bundle {
    val start = Bool(INPUT)
    val elemCount = UInt(INPUT, width = 32)
    val threshCount = UInt(INPUT, width = 32)
    val in = Decoupled(UInt(INPUT, width = w)).flip
    val finished = Bool(OUTPUT)
    val out = Decoupled(UInt(OUTPUT, width = w))
    val writerStart = Bool(OUTPUT)
    val readerStart = Bool(OUTPUT)
  }

  val thresholder = Module(new Thresholder(w)).io

  val bytesPerElem = w / 8

  val sIdle :: sReadThreshold :: sReadMatrix :: sApplyThreshold :: sFinished :: Nil = Enum(UInt(), 5)

  val rState = Reg(init = UInt(sIdle))
  val rThresholds = Vec.fill(255) { Reg(init = UInt(0, width = w)) }
  val rOut = Reg(init = UInt(0, width = w))
  val rIndex = Reg(init = UInt(0, 32))
  val rMatrixElem = Reg(init = UInt(0, width = w))
  val rMatrixValid = Reg(init = Bool(false))
  val rThresholdStart = Reg(init = Bool(false))
  // val rReaderStart = Reg(init = Bool(false))
  // val rWriterStart = Reg(init = Bool(false))

  io.finished := Bool(false)
  io.in.ready := Bool(false)
  io.out.valid := Bool(false)
  io.writerStart := Bool(false)
  io.readerStart := Bool(false)
  io.out.bits := thresholder.count.bits
  // io.writerStart := rWriterStart
  // io.readerStart := rReaderStart

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
        io.readerStart := Bool(true)
        io.writerStart := Bool(true)
        // rReaderStart := Bool(true)
        // rWriterStart := Bool(true)
        rState := sReadThreshold
      }
    }
    is (sReadThreshold) {
      when (rIndex === UInt(io.threshCount)) {
        rState := sReadMatrix
      }
      .otherwise {
        io.in.ready := Bool(true)
        when (io.in.valid) {
          rThresholds(rIndex) := io.in.bits
          rIndex := rIndex + UInt(1)
        }
      }
    }
    is (sReadMatrix) {
      when (rIndex === UInt(io.elemCount)) {
        rState := sFinished
      }
      .otherwise {
        io.in.ready := Bool(true)
        when (io.in.valid) {
          rIndex := rIndex + UInt(1)
          rMatrixElem := io.in.bits
          rMatrixValid := Bool(true)
          rThresholdStart := Bool(true)
          rState := sApplyThreshold
        }
      }
    }
    is (sApplyThreshold) {
      when (thresholder.count.valid) {
        io.out.valid := Bool(true)
        when (io.out.ready && thresholder.matrix.ready) {
          rThresholdStart := Bool(false)
          rMatrixValid := Bool(false)
          rState := sReadMatrix
        }
      }
    }
    is (sFinished) {
      io.finished := Bool(true)
      // rReaderStart := Bool(false)
      // rWriterStart := Bool(false)
      when (!io.start) {
        rState := sIdle
      }
    }
  }
}


class Thresholder(w: Int) extends Module {
  val io = new Bundle {
    val matrix = Decoupled(UInt(INPUT, width = w)).flip()
    val size = UInt(INPUT, width = 32)
    val threshold = Vec.fill(255) { UInt(INPUT, width = w) }
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val count = Decoupled(UInt(OUTPUT, width = 32))
    val cc = UInt(OUTPUT, width = 32)
  }

  val sIdle :: sRunning :: sFinished :: Nil = Enum(UInt(), 3)

  val rState = Reg(init = sIdle)
  val rCount = Reg(init = UInt(0, 32))
  val rIndex = Reg(init = UInt(0, 32))
  val rCC = Reg(init = UInt(0, 32))

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
      .elsewhen (io.matrix.valid) {
        rCount := rCount +
          (UInt(io.matrix.bits) >= UInt(io.threshold(rIndex)))
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
