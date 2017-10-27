package rosetta

import Chisel._
import fpgatidbits.dma._
import fpgatidbits.streams._
import fpgatidbits.PlatformWrapper._


/** Reades a single integer to DRAM.
 */
class StreamReaderUnit extends RosettaAccelerator {
  val numMemPorts = 1
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val baseAddr = UInt(INPUT, width = 64)
    val byteCount = UInt(INPUT, width = 32)
    val sum = UInt(OUTPUT, width = 32)
    val cc = UInt(OUTPUT, width = 32)
  }

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

  // Param 1: Stream width.
  // Param 2: Inital value of the reducer.
  // Param 3: Reduction operator.
  val reducer = Module(new StreamReducer(32, 0, {_+_})).io

  // Wire up the stream reader and reducer to the parameters that will be
  // specified by the user at runtime.
  reader.start := io.start
  reducer.start := io.start
  reader.baseAddr := io.baseAddr

  reader.byteCount := io.byteCount
  reducer.byteCount := io.byteCount

  // Expose reduction result.
  io.sum := reducer.reduced
  io.finished := reducer.finished

  // Wire up the read requests-responses against the memory port interface.
  reader.req <> io.memPort(0).memRdReq
  io.memPort(0).memRdRsp <> reader.rsp

  // Push the read stream into the reducer.
  reader.out <> reducer.streamIn

  // Plug the unused write port.
  io.memPort(0).memWrReq.valid := Bool(false)
  io.memPort(0).memWrDat.valid := Bool(false)
  io.memPort(0).memWrRsp.ready := Bool(false)

  val r_cc = Reg(init = UInt(0, 32))

  io.cc := r_cc

  when (!io.start) {
    r_cc := UInt(0)
  }
  .elsewhen (io.start & !io.finished) {
    r_cc := r_cc + UInt(1)
  }

  io.signature := makeDefaultSignature()
}


class StreamWriterUnit extends RosettaAccelerator {
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
