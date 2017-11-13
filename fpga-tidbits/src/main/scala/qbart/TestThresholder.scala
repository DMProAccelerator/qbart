package fpgatidbits.Testbenches

import Chisel._
import fpgatidbits.PlatformWrapper._


class TestThresholder(p: PlatformWrapperParams) extends GenericAccelerator(p) {
  val numMemPorts = 0
  val io = new GenericAcceleratorIF(numMemPorts, p) {
    val matrix = Decoupled(UInt(INPUT, width = 32)).flip()
    val size = UInt(INPUT, width = 32)
    val threshold = Vec.fill(4) { UInt(INPUT, width = 32) }
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

  // Default values
  io.signature := makeDefaultSignature()
  io.finished := Bool(false)
  io.count.valid := Bool(false)
  io.matrix.ready := Bool(false)
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
      when (rIndex === UInt(io.size)) {
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

  // Benchmarking.
  io.cc := rCC
  when (io.start & !io.finished) {
    rCC := rCC + UInt(1)
  }
}
