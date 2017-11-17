package rosetta

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.streams._


class TestDMAThresholder extends RosettaAccelerator {
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

    // DEBUG
    val threshold_out_valid = Bool(OUTPUT)
    val threshold_state_out = UInt(OUTPUT)
    val mem_out_ready = Bool(OUTPUT)
    val mem_in_valid = Bool(OUTPUT)
    val state_out = UInt(OUTPUT)
    val reader_valid = Bool(OUTPUT)

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

  //DEBUG
  io.threshold_out_valid := handler.threshold_out_valid
  io.threshold_state_out := handler.threshold_state_out
  io.mem_out_ready := handler.mem_out_ready
  io.mem_in_valid := handler.mem_in_valid
  io.reader_valid := reader.out.valid
  io.state_out := handler.state_out


  reader.out <> handler.in
  reader.req <> io.memPort(0).memRdReq
  io.memPort(0).memRdRsp <> reader.rsp
  plugMemWritePort(0)

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

    // DEBUG
    val threshold_out_valid = Bool(OUTPUT)
    val threshold_state_out = UInt(OUTPUT)
    val mem_out_ready = Bool(OUTPUT)
    val mem_in_valid = Bool(OUTPUT)
    val state_out = UInt(OUTPUT)
  }
  val numCompareUnits = 8

  val thresholder = Module(new Threshold( numCompareUnits )).io

  val bytesPerElem = w / 8

  val sIdle :: sReadThreshold :: sReadMatrix :: sApplyThreshold :: sFinished :: Nil = Enum(UInt(), 5)

  val rState = Reg(init = UInt(sIdle))
  val rThresholds = Vec.fill(32) { Reg(init = UInt(0, width = w)) }
  val rOut = Reg(init = UInt(0, width = w))
  val rIndex = Reg(init = UInt(0, 32))

  val rThresholdStart = Reg(init = Bool(false))
  val rThresholdIndex = Reg(init = UInt(0, width = 8))
  val rThresholdValid = Reg(init = Bool(false))
  val rOutReady = Reg(init = Bool(false))

  // DEBUG
  io.threshold_out_valid := thresholder.out.valid
  io.threshold_state_out := thresholder.state_out
  io.mem_out_ready := io.out.ready
  io.mem_in_valid := io.in.valid
  io.state_out := rState

  rOutReady := Bool(false)

  io.finished := Bool(false)
  io.in.ready := Bool(false)
  io.out.valid := Bool(false)
  io.out.bits := thresholder.out.bits
  io.writerStart := Bool(false)
  io.readerStart := Bool(false)

  thresholder.element.bits := UInt(255)
  thresholder.element.valid := Bool(false)
  thresholder.start := Bool(false)
  thresholder.thresh.valid := rThresholdValid
  thresholder.out.ready := rOutReady

  for ( i <- 0 until numCompareUnits ) {
      thresholder.thresh.bits.in(i) := rThresholds( rThresholdIndex + UInt(i) )
      thresholder.thresh.bits.en(i) := Bool(true)

      when( rThresholdIndex + UInt(i) >= io.threshCount ) {
          thresholder.thresh.bits.en(i) := Bool(false)
      }
  }

  switch (rState) {
    is (sIdle) {
      rIndex := UInt(0)
      when (io.start) {
        io.writerStart := Bool(true)
        io.readerStart := Bool(true)
        rState := sReadThreshold
      }
    }
    is (sReadThreshold) {
      when (rIndex === io.threshCount) {
        rState := sReadMatrix
      }
      .otherwise {
        when (io.in.valid) {
            io.in.ready := Bool(true)
          rThresholds(rIndex) := io.in.bits
          rIndex := rIndex + UInt(1)
        }
      }
    }
    is (sReadMatrix) {
      when (rIndex === io.elemCount) {
        rState := sFinished
      }
      .otherwise {
          when(thresholder.element.ready) {

            when (io.in.valid) {
                io.in.ready := Bool(true)
              rIndex := rIndex + UInt(1)

              thresholder.element.bits := io.in.bits
              thresholder.element.valid := Bool(true)
              thresholder.start := Bool(true)

              rThresholdValid := Bool(true)
              rThresholdIndex := UInt(0)

              rState := sApplyThreshold
          }
        }
      }
    }
    is (sApplyThreshold) {
        rThresholdValid := Bool(true)
        rThresholdIndex := rThresholdIndex + UInt(numCompareUnits)


      when (thresholder.out.valid) {
        io.out.valid := Bool(true)
        rThresholdValid := Bool(false)

        when (io.out.ready) {
          rOutReady := Bool(true)
          rState := sReadMatrix
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