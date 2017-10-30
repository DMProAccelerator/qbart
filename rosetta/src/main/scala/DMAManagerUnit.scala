package rosetta

import Chisel._
import fpgatidbits.dma._
import fpgatidbits.streams._
import fpgatidbits.PlatformWrapper._


class StreamReaderUnit extends RosettaAccelerator {
  val numMemPorts = 1
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val baseAddr = UInt(INPUT, width = 64)
    val byteCount = UInt(INPUT, width = 32)
    val out = UInt(OUTPUT, width = 32)
    val cc = UInt(OUTPUT, width = 32)
  }

  val rCC = Reg(init = UInt(0, 32))

  // Param 4: Burstmode. Set to > 1 for better DRAM bandwidth.
  // Param 5: Stream ID for distinguishing between returned responses.
  val reader_params = new StreamReaderParams(
    streamWidth = 32,
    fifoElems = 8,
    mem = PYNQParams.toMemReqParams(),
    maxBeats = 1,
    chanID = 0,
    disableThrottle = true
  )

  val reader = Module(new StreamReader(reader_params)).io
  val scheduler = Module(new ThresholdSchedulerUnit()).io

  reader.start := io.start
  scheduler.start := io.start
  reader.baseAddr := io.baseAddr

  reader.byteCount := io.byteCount
  scheduler.byteCount := io.byteCount

  io.out := scheduler.reduced
  io.finished := scheduler.finished

  reader.req <> io.memPort(0).memRdReq
  io.memPort(0).memRdRsp <> reader.rsp

  reader.out <> scheduler.streamIn

  io.memPort(0).memWrReq.valid := Bool(false)
  io.memPort(0).memWrDat.valid := Bool(false)
  io.memPort(0).memWrRsp.ready := Bool(false)

  io.signature := makeDefaultSignature()

  io.cc := rCC
  when (!io.start) {
    rCC := UInt(0)
  }
  .elsewhen (io.start & !io.finished) {
    rCC := rCC + UInt(1)
  }
  io.signature := makeDefaultSignature()
}


class ThresholdSchedulerUnit extends Module {
  val io = new Bundle {
    val start = Bool(INPUT)
    val byteCount = UInt(INPUT, width = 32)
    val finished = Bool(OUTPUT)
    val reduced = UInt(OUTPUT, width = 32)
    val streamIn = Decoupled(UInt(width = 32)).flip
  }

  val bytesPerElem = 32 / 8

  val sIdle :: sRunning :: sFinished :: Nil = Enum(UInt(), 3)

  val rState = Reg(init = UInt(sIdle))
  val rReduced = Reg(init = UInt(0, width = 32))
  val rBytesLeft = Reg(init = UInt(0, 32))
  var rIndex = Reg(init = UInt(0, 32))

  io.finished := Bool(false)
  io.reduced := rReduced
  io.streamIn.ready := Bool(false)

  switch (rState) {
    is (sIdle) {
      rReduced := UInt(0)
      rIndex := UInt(0)
      rBytesLeft := io.byteCount
      when (io.start) { rState := sRunning }
    }
    is (sRunning) {
      when (rIndex === UInt(bytesPerElem * 3)) { rState := sFinished }
      .otherwise {
        io.streamIn.ready := Bool(true)
        when (io.streamIn.valid) {
          rReduced := rReduced + UInt(io.streamIn.bits)
          rBytesLeft := rBytesLeft - UInt(bytesPerElem)
          rIndex := rIndex + UInt(bytesPerElem)
        }
      }
    }
    is (sFinished) {
      io.finished := Bool(true)
      when (!io.start) { rState := sIdle}
    }
  }

  io.signature := makeDefaultSignature()

  io.cc := rCC
  when (!io.start) {
    rCC := UInt(0)
  }
  .elsewhen (io.start & !io.finished) {
    rCC := rCC + UInt(1)
  }
  io.signature := makeDefaultSignature()
}


class StreamWriterUnit extends RosettaAccelerator {
  val numMemPorts = 1
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val baseAddr = UInt(INPUT, width=64)
    val start = Bool(INPUT)
    val out = UInt(OUTPUT, width=32)
    val finished = Bool(OUTPUT)
    val cc = UInt(OUTPUT, width=32)
  }

  io.signature := makeDefaultSignature()

  val rOut = Reg(init = UInt(42, 32))
  val rCC = Reg(init = UInt(0, 32))

  rCC := rCC + UInt(1)
  io.cc := rCC

  val writerParams = new StreamWriterParams(
    streamWidth = 64,
    mem = PYNQParams.toMemReqParams(),
    chanID = 0
  )

  val writer = Module(new StreamWriter(writerParams)).io

  when (io.start) {
    io.out := rOut
  }
  .otherwise {
    io.out := UInt(0)
  }

  writer.baseAddr := io.baseAddr
  writer.byteCount := UInt(8)
  writer.start := io.start
  writer.req <> io.memPort(0).memWrReq
  writer.wdat <> io.memPort(0).memWrDat

  io.memPort(0).memWrRsp <> writer.rsp

  writer.in.bits := rOut
  writer.in.valid := io.start

  io.finished := writer.finished

  io.signature := makeDefaultSignature()

  io.cc := rCC
  when (!io.start) {
    rCC := UInt(0)
  }
  .elsewhen (io.start & !io.finished) {
    rCC := rCC + UInt(1)
  }
  io.signature := makeDefaultSignature()
}
