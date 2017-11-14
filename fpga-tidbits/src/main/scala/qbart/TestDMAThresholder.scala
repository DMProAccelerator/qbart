package fpgatidbits.Testbenches

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.streams._


class TestDMAThresholder(p: PlatformWrapperParams) extends GenericAccelerator(p) {
  val numMemPorts = 2
  val io = new GenericAcceleratorIF(numMemPorts, p) {
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
    fifoElems = 10,
    mem = p.toMemReqParams(),
    maxBeats = 10,
    chanID = 0,
    disableThrottle = true
  ))).io
  val writer = Module(new StreamWriter(new StreamWriterParams(
    streamWidth = 64,
    mem = p.toMemReqParams(),
    chanID = 0
  ))).io
  val handler = Module(new DMAHandler(64)).io

  reader.start := io.start
  reader.baseAddr := io.baseAddrRead
  reader.byteCount := io.byteCount

  writer.start := io.start
  writer.baseAddr := io.baseAddrWrite
  writer.byteCount := io.byteCount

  handler.start := io.start
  handler.elemCount := io.elemCount
  handler.threshCount := io.threshCount

  io.signature := makeDefaultSignature()
  io.finished := handler.finished

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
  }
  val numCompareUnits = 32

  val thresholder = Module(new Threshold( numCompareUnits )).io

  val bytesPerElem = w / 8

  val sIdle :: sReadThreshold :: sReadMatrix :: sApplyThreshold :: sFinished :: Nil = Enum(UInt(), 5)

  val rState = Reg(init = UInt(sIdle))
  val rThresholds = Vec.fill(20) { Reg(init = UInt(0, width = w)) }
  val rOut = Reg(init = UInt(0, width = w))
  val rIndex = Reg(init = UInt(0, 32))

  val rThresholdStart = Reg(init = Bool(false))
  val rThresholdIndex = Reg(init = UInt(0, width = 32))
  val rThresholdValid = Reg(init = Bool(false))
  val rOutReady = Reg(init = Bool(false))

    // printf("State %d   runThreshold %d\n", rState, sApplyThreshold)
    // printf("rIndex %d\n", rIndex)
  rOutReady := Bool(false)

  io.finished := Bool(false)
  io.in.ready := Bool(false)
  io.out.valid := Bool(false)
  io.out.bits := thresholder.out.bits

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
          when(thresholder.element.ready) {
              io.in.ready := Bool(true)

            when (io.in.valid) {
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
