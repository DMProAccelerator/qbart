package fpgatidbits.Testbenches

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.streams._

class TestDMAWriter(p: PlatformWrapperParams) extends GenericAccelerator(p) {
  val numMemPorts = 1
  val io = new GenericAcceleratorIF(numMemPorts, p) {
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val baseAddr = UInt(INPUT, width = 64)
    val count = UInt(INPUT, width = 32)
    val out = UInt(OUTPUT, width = 32)
    val cc = UInt(OUTPUT, width = 32)
  }

  plugMemReadPort(0)

  val writer = Module(new StreamWriter(new StreamWriterParams(
    streamWidth = 64,
    mem = p.toMemReqParams(),
    chanID = 0
  ))).io
  val sequence = Module(new Sequence(64)).io

  val rCC = Reg(init = UInt(0, 32))

  writer.start := io.start
  writer.baseAddr := io.baseAddr
  writer.byteCount := io.count * UInt(8)

  io.finished := writer.finished
  sequence.start := io.start
  sequence.count := io.count

  sequence.seq <> writer.in
  writer.req <> io.memPort(0).memWrReq
  writer.wdat <> io.memPort(0).memWrDat
  io.memPort(0).memWrRsp <> writer.rsp

  io.signature := makeDefaultSignature()
}

class Sequence(w: Int) extends Module {
  val io = new Bundle {
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val count = UInt(INPUT, width = 32)
    val seq = Decoupled(UInt(OUTPUT, width = w))
  }

  val sIdle :: sRunning :: sFinished :: Nil = Enum(UInt(), 3)

  val rState = Reg(init = UInt(sIdle))
  val rSeq = Reg(init = UInt(0, w))
  val rIndex = Reg(init = UInt(0, 32))

  io.finished := Bool(false)
  io.seq.valid := Bool(false)
  io.seq.bits := rSeq

  switch (rState) {
    is (sIdle) {
      when (io.start) {
        rState := sRunning
        rIndex := UInt(0)
        rSeq := UInt(1)
      }
    }
    is (sRunning) {
      when (rIndex === io.count) {
        rState := sFinished
      }
      .otherwise {
        io.seq.valid := Bool(true)
        when (io.seq.ready) {
          rIndex := rIndex + UInt(1)
          rSeq := rSeq + UInt(1)
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
