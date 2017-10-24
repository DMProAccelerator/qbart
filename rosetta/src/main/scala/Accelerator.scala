package rosetta

import Chisel._

// add your custom accelerator here, derived from RosettaAccelerator

// here we have a test for register reads and writes: add two 64-bit values
// the io bundle has the following signals:
// op: vector of two 64-bit signals, input values to be added
// sum: output 64-bit signal, equal to op(0)+op(1)
// cc: the number of clock cycles that have elapsed since last reset
class TestRegOps() extends RosettaAccelerator {
  val numMemPorts = 0
  val io = new RosettaAcceleratorIF(numMemPorts) {
    val op = Vec.fill(2) {UInt(INPUT, width = 64)}
    val sum = UInt(OUTPUT, width = 64)
    val cc = UInt(OUTPUT, width = 32)
  }
  // wire sum output to sum of op inputs
  io.sum := io.op(0) + io.op(1)

  // instantiate a clock cycle counter register
  val regCC = Reg(init = UInt(0, 32))
  // increment counter by 1 every clock cycle
  regCC := regCC + UInt(1)
  // expose counter through the output called cc
  io.cc := regCC

  // turn on colored lights when switches are activated
  io.led4(0) := io.sw(0)
  io.led5(1) := io.sw(0)
  io.led4(1) := io.sw(1)
  io.led5(2) := io.sw(1)
  // in addition to the signals we defined here, there are some signals that
  // are always present in the io bundle, as we derive from RosettaAcceleratorIF

  // the signature can be e.g. used for checking that the accelerator has the
  // correct version. here the signature is regenerated from the current date.
  io.signature := makeDefaultSignature()
  // use the buttons to control the LEDs
  io.led := io.btn
}


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
