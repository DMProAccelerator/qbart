package rosetta

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.streams._


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
  }

  val thresholder = Module(new Thresholder(w)).io

  val bytesPerElem = w / 8

  val sIdle :: sReadThreshold :: sReadMatrix :: sApplyThreshold :: sReaderFlush :: sFinished :: Nil = Enum(UInt(), 6)

  val rState = Reg(init = UInt(sIdle))
  val rThresholds = Vec.fill(20) { Reg(init = SInt(0, width = w)) }
  val rIndex = Reg(init = UInt(0, 32))
  val rMatrixElem = Reg(init = UInt(0, width = w))
  val rMatrixValid = Reg(init = Bool(false))
  val rThresholdStart = Reg(init = Bool(false))
  val rBytesLeft = Reg(init = UInt(0, 32))

  io.reader.baseAddr := io.baseAddrRead
  io.reader.byteCount := io.byteCountReader
  io.writer.baseAddr := io.baseAddrWrite
  io.writer.byteCount := io.byteCountWriter

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
        rBytesLeft := io.byteCountReader
        io.reader.start := Bool(true)
        io.writer.start := Bool(true)
      }
    }
    is (sReadThreshold) {
      when (rIndex === UInt(io.threshCount)) {
        rState := sReadMatrix
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
      //.otherwise (thresholder.matrix.ready) {
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
    val threshold = Vec.fill(255) { SInt(INPUT, width = w) }
    val matrix    = Decoupled(UInt(INPUT, width = w)).flip()
    val count     = Decoupled(UInt(OUTPUT, width = w))
    val finished  = Bool(OUTPUT)
  }

  val sIdle :: sRunning :: sFinished :: Nil = Enum(UInt(), 3)

  val rState = Reg(init = sIdle)
  val rCount = Reg(init = SInt(0, 32))
  val rIndex = Reg(init = UInt(0, 32))

  io.finished := Bool(false)
  io.matrix.ready := Bool(false)
  io.count.valid := Bool(false)
  io.count.bits := rCount

  switch (rState) {
    is (sIdle) {
      rCount := UInt(0)
      rIndex := UInt(0)
      io.matrix.ready := Bool(true)
      when (io.start) {
        rState := sRunning
      }
    }
    is (sRunning) {
      when (rIndex === io.size) {
        rState := sFinished
      }
      .elsewhen (io.matrix.valid) {
        rCount := rCount + (UInt(io.matrix.bits) >= UInt(io.threshold(rIndex)))
        rIndex := rIndex + UInt(1)
      }
    }
    is (sFinished) {
      io.finished := Bool(true)
      when (io.count.ready & !io.start) {
        rState := sIdle
      }
      .otherwise {
        io.count.valid := Bool(true)
      }
    }
  }
}
