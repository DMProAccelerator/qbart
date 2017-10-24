package rosetta

import Chisel._
import fpgatidbits.dma._
import fpgatidbits.streams._
import fpgatidbits.PlatformWrapper._


/** Writes a single integer to DRAM.
 */
class DMAManagerUnit extends RosettaAccelerator {
  val numMemPorts = 1
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val addr = UInt(INPUT, width=64)
    val start = Bool(INPUT)
    val out = UInt(OUTPUT, width=32)
    val finished = Bool(OUTPUT)
    val writer_error = Bool(OUTPUT)
    val writer_ready = Bool(OUTPUT)
    val cc = UInt(OUTPUT, width=32)
  }

  io.signature := makeDefaultSignature()

  val r_out = Reg(init = UInt(42, 32))
  val r_cc = Reg(init = UInt(0, 32))

  r_cc := r_cc + UInt(1)
  io.cc := r_cc

  val wp = new StreamWriterParams(
    streamWidth = 64,
    mem = PYNQParams.toMemReqParams(),
    chanID = 0
  )

  val writer = Module(new StreamWriter(wp)).io

  when(io.start){
    io.out := r_out
  }
  .otherwise{
    io.out := UInt(0)
  }

  writer.baseAddr := io.addr
  writer.byteCount := UInt(8)
  writer.start := io.start
  writer.req <> io.memPort(0).memWrReq
  writer.wdat <> io.memPort(0).memWrDat

  io.memPort(0).memWrRsp <> writer.rsp
  plugMemReadPort(0)

  writer.in.bits := r_out
  writer.in.valid := io.start

  io.writer_error := writer.error
  io.writer_ready := writer.in.ready

  io.finished := writer.finished
}
