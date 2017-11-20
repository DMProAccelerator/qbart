package fpgatidbits.Testbenches

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.streams._


class TestDMAThresholder(p: PlatformWrapperParams) extends GenericAccelerator(p) {
  val numMemPorts = 2
  val w = 64
  val io = new GenericAcceleratorIF(numMemPorts, p) {
    val start           = Bool(INPUT)
    val baseAddrRead    = UInt(INPUT, width = w)
    val baseAddrWrite   = UInt(INPUT, width = w)
    val byteCount       = UInt(INPUT, width = 32)
    val byteCountReader = UInt(INPUT, width = 32)
    val byteCountWriter = UInt(INPUT, width = 32)
    val elemCount       = UInt(INPUT, width = 32)
    val threshCount     = UInt(INPUT, width = 32)
    val sum             = UInt(OUTPUT, width = 32)
    val cc              = UInt(OUTPUT, width = 32)
    val finished        = Bool(OUTPUT)

    // // DEBUG
    // val threshold_out_valid = Bool(OUTPUT)
    // val threshold_out = UInt(OUTPUT)
    // val matrix_out = UInt(OUTPUT)
    // val threshold_state_out = UInt(OUTPUT)
    // val state_out = UInt(OUTPUT)
    // val reader_out_valid = Bool(OUTPUT)
    // val index_out = UInt(OUTPUT)

  }



  val rCC = Reg(init = UInt(0, 32))

  val reader = Module(new StreamReader(new StreamReaderParams(
    streamWidth = w,
    fifoElems = 8,
    mem = p.toMemReqParams(),
    maxBeats = 1,
    chanID = 0,
    disableThrottle = true
  ))).io
  val writer = Module(new StreamWriter(new StreamWriterParams(
    streamWidth = w,
    mem = p.toMemReqParams(),
    chanID = 0
  ))).io
  val handler = Module(new DMAHandler(w, p)).io

  handler.start := io.start
  handler.byteCount := io.byteCount
  handler.byteCountReader := io.byteCountReader
  handler.byteCountWriter := io.byteCountWriter
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


  // //DEBUG
  // io.threshold_out_valid := handler.threshold_out_valid
  // io.threshold_out := handler.threshold_out
  // io.matrix_out := handler.matrix_out
  // io.threshold_state_out := handler.threshold_state_out
  // io.state_out := handler.state_out
  // io.reader_out_valid := handler.reader_out_valid
  // io.index_out := handler.index_out

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
    val start           = Bool(INPUT)
    val byteCount       = UInt(INPUT, width = 32)
    val byteCountReader = UInt(INPUT, width = 32)
    val byteCountWriter = UInt(INPUT, width = 32)
    val elemCount       = UInt(INPUT, width = 32)
    val threshCount     = UInt(INPUT, width = 32)
    val baseAddrRead    = UInt(INPUT, width = w)
    val baseAddrWrite   = UInt(INPUT, width = w)
    val finished        = Bool(OUTPUT)
    var reader          = new StreamReaderIF(w, p.toMemReqParams).flip()
    var writer          = new StreamWriterIF(w, p.toMemReqParams).flip()

    // // DEBUG
    // val threshold_out_valid = Bool(OUTPUT)
    // val threshold_state_out = UInt(OUTPUT)
    // val state_out = UInt(OUTPUT)
    // val reader_out_valid = Bool(OUTPUT)
    // val threshold_out = UInt(OUTPUT)
    // val matrix_out = UInt(OUTPUT)
    // val index_out = UInt(OUTPUT)
  }



  val numCompareUnits = 4

  val thresholder = Module(new Threshold( numCompareUnits )).io

  val bytesPerElem = w / 8

  val sIdle :: sReadThreshold :: sReadMatrix :: sApplyThreshold :: sReaderFlush :: sFinished :: Nil = Enum(UInt(), 6)

  val rState = Reg(init = UInt(sIdle))
  val rThresholds = Vec.fill(20) { Reg(init = UInt(0, width = w)) }
  val rIndex = Reg(init = UInt(0, 32))

  val rThresholdStart = Reg(init = Bool(false))
  val rThresholdIndex = Reg(init = UInt(0, width = 32))
  val rThresholdValid = Reg(init = Bool(false))
  val rThresholdOutReady = Reg(init = Bool(false))
  val rBytesLeft = Reg(init = UInt(0, 32))

  rIndex := rIndex
  rThresholdIndex := rThresholdIndex
  rBytesLeft := rBytesLeft

  thresholder.start := Bool(false)
  thresholder.element.bits := UInt(255)
  thresholder.element.valid := Bool(false)

  thresholder.thresh.valid := Bool(false)

  for ( i <- 0 until numCompareUnits ) {
      thresholder.thresh.bits.in(i) := rThresholds( rThresholdIndex + UInt(i) )
      thresholder.thresh.bits.en(i) := Bool(true)

      when( rThresholdIndex + UInt(i) >= io.threshCount ) {
          thresholder.thresh.bits.en(i) := Bool(false)
      }
  }

  io.reader.baseAddr := io.baseAddrRead
  io.reader.byteCount := io.byteCountReader
  io.writer.baseAddr := io.baseAddrWrite
  io.writer.byteCount := io.byteCountWriter

  io.reader.out.ready := Bool(false)
  io.finished := Bool(false)
  io.writer.start := Bool(false)
  io.reader.start := Bool(false)

  io.writer.in.bits := thresholder.out.bits
  io.writer.in.valid := Bool(false)
  thresholder.out.ready := Bool(false)

  // printf("state %d      threshcount %d     elemcount  %d     index %d     bytes left %d\n", rState, io.threshCount, io.elemCount, rIndex, rBytesLeft);

  // thresholder.out <> io.writer.in
  //thresholder.out <> io.writer.in
  // thresholder.matrix.bits := rMatrixElem
  // thresholder.matrix.valid := rMatrixValid
  // thresholder.start := rThresholdStart
  // thresholder.size := io.threshCount
  //
  // for (i <- 0 to rThresholds.size - 1) {
  //   thresholder.threshold(i) := rThresholds(i)
  // }

  // printf("State outside: %d\n", rState)


  // // DEBUG
  // io.threshold_out_valid := thresholder.out.valid
  // io.threshold_out := thresholder.out.bits
  // io.threshold_state_out := thresholder.state_out
  // io.state_out := rState
  // io.reader_out_valid := io.reader.out.valid
  // io.matrix_out := thresholder.matrix_out
  // io.index_out := rIndex


  switch (rState) {
    is (sIdle) {
      rIndex := UInt(0)
      when (io.start) {
        rState := sReadThreshold
        rBytesLeft := io.byteCountReader
        io.reader.start := Bool(true)
        io.writer.start := Bool(true)
      }
    }
    is (sReadThreshold) {

      when (rIndex === io.threshCount) {
        rIndex := rIndex
        rState := sReadMatrix
      }
      .elsewhen (io.reader.out.valid) {
        io.reader.out.ready := Bool(true)
        rThresholds(rIndex) := io.reader.out.bits
        rIndex := rIndex + UInt(1)
        rBytesLeft := rBytesLeft - UInt(bytesPerElem)

      }
    }
    is (sReadMatrix) {
      when (rIndex === UInt(io.elemCount)) {
        rState := sReaderFlush
      }
      .elsewhen (thresholder.element.ready) {

        when (io.reader.out.valid) {
          io.reader.out.ready := Bool(true)

          thresholder.start := Bool(true)
          thresholder.element.bits := io.reader.out.bits
          thresholder.element.valid := Bool(true)
          rThresholdIndex := UInt(0)

          rIndex := rIndex + UInt(1)
          rBytesLeft := rBytesLeft - UInt(bytesPerElem)
          rState := sApplyThreshold
        }
      }
    }
    is (sApplyThreshold) {
      when (thresholder.out.valid && io.writer.in.ready) {
        io.writer.in.valid := Bool(true)
        thresholder.out.ready := Bool(true)

        rState := sReadMatrix
      }
      .otherwise {
        thresholder.thresh.valid := Bool(true)
        rThresholdIndex := rThresholdIndex + UInt(numCompareUnits)
      }
    }
    is (sReaderFlush) {
      when (rBytesLeft === UInt(0)) {
        rState := sFinished
      }
      .otherwise {
        when (io.reader.out.valid) {
          io.reader.out.ready := Bool(true)
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

// class Thresholder(w: Int) extends Module {
//   val io = new Bundle {
//     val start     = Bool(INPUT)
//     val size      = UInt(INPUT, width = 32)
//     val threshold = Vec.fill(255) { UInt(INPUT, width = w) }
//     val matrix    = Decoupled(UInt(INPUT, width = w)).flip()
//     val count     = Decoupled(UInt(OUTPUT, width = w))
//     val finished  = Bool(OUTPUT)
//   }
//
//   val sIdle :: sRunning :: sFinished :: Nil = Enum(UInt(), 3)
//
//   val rState = Reg(init = sIdle)
//   val rCount = Reg(init = UInt(0, 32))
//   val rIndex = Reg(init = UInt(0, 32))
//
//   io.finished := Bool(false)
//   io.matrix.ready := Bool(false)
//   io.count.valid := Bool(false)
//   io.count.bits := rCount
//
//   switch (rState) {
//     is (sIdle) {
//       rCount := UInt(0)
//       rIndex := UInt(0)
//       io.matrix.ready := Bool(true)
//       when (io.start) {
//         rState := sRunning
//       }
//     }
//     is (sRunning) {
//       when (rIndex === io.size) {
//         rState := sFinished
//       }
//       .elsewhen (io.matrix.valid) {
//         rCount := rCount + (UInt(io.matrix.bits) >= UInt(io.threshold(rIndex)))
//         rIndex := rIndex + UInt(1)
//       }
//     }
//     is (sFinished) {
//       io.finished := Bool(true)
//       when (io.count.ready & !io.start) {
//         rState := sIdle
//       }
//       .otherwise {
//         io.count.valid := Bool(true)
//       }
//     }
//   }
// }
