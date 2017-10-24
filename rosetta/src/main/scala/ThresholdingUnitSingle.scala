package rosetta

import Chisel._


/** Compares two values and accumulates their corresponding Hamming weight.
 *
 * The class provides a hard step function that compares a matrix element
 * M(i, j) to a threshold element T(k). If M(i, j) >= T(k), then the Hamming
 * weight is incremented by one.
 */
class ThresholdingUnit extends RosettaAccelerator {
  val numMemPorts = 0
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val matrix = Decoupled(UInt(INPUT, width = 32)).flip()
    val size = UInt(INPUT, width = 32)
    val op = Vec.fill(4) { UInt(INPUT, width = 32) }
    val start = Bool(INPUT)
    val count = Decoupled(UInt(OUTPUT, width = 32))
    val cc = UInt(OUTPUT, width = 32)
  }

  val s_idle :: s_running :: s_finished :: Nil = Enum(UInt(), 3)

  val r_state = Reg(init = s_idle)
  val r_accumulator = Reg(init = UInt(0, 32))
  val r_index = Reg(init = UInt(0, 32))
  val r_cc = Reg(init = UInt(0, 32))

  io.signature := makeDefaultSignature()

  r_cc := r_cc + UInt(1)
  io.cc := r_cc

  // Default values.
  io.count.valid := Bool(false)
  io.matrix.ready := Bool(false)
  io.count.bits := r_accumulator

  switch (r_state) {
    is (s_idle) {
      r_accumulator := UInt(0)
      r_index := UInt(0)
      when (io.start) {
        r_state := s_running
      }
    }
    is (s_running) {
      when (r_index === UInt(io.size)) {
        r_state := s_finished
      }
      .elsewhen (io.matrix.valid) {
        r_accumulator := r_accumulator +
          (UInt(io.matrix.bits) >= UInt(io.op(r_index)))
        r_index := r_index + UInt(1)
      }
    }
    is (s_finished) {
      when (!io.start) {
        r_state := s_idle
      }
      .otherwise {
        io.count.valid := Bool(true)
        io.matrix.ready := Bool(true)
      }
    }
  }
}
