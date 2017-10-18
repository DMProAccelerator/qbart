package Prototypes

import Chisel._

/** Compares two values. */
object PopCount {
    def compare(a: UInt, b: UInt): UInt = {
        a >= b
    }
}


/** Compares two values and accumulates their corresponding Hamming weight.
 *
 * The class provides a hard step function that compares a matrix element
 * M(i, j) to a threshold element T(k). If M(i, j) >= T(k), then the Hamming
 * weight is incremented by one.
 */
class Accumulator extends Module {
    val io = new Bundle {
        val matrix = Decoupled(UInt(INPUT, width = 32)).flip()
        val threshold = Decoupled(UInt(INPUT, width = 32)).flip()
        val size = UInt(INPUT, width = 32)
        val start = Bool(INPUT)
        val count = Decoupled(UInt(OUTPUT, width = 32))
    }

    val s_idle :: s_running :: s_finished :: Nil = Enum(UInt(), 3)

    val r_state = Reg(init = s_idle)
    val r_accumulator = Reg(init = UInt(0, 32))
    var r_index = Reg(init = UInt(0, 32))

    // Default values.
    io.count.valid := Bool(false)
    io.matrix.ready := Bool(false)
    io.threshold.ready := Bool(false)
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
            .elsewhen (io.matrix.valid & io.threshold.valid) {
                r_accumulator := r_accumulator + PopCount.compare(io.matrix.bits, io.threshold.bits)
                r_index := r_index + UInt(1)
                io.matrix.ready := Bool(true)
                io.threshold.ready := Bool(true)
            }
        }
        is (s_finished) {
            when (!io.start) {
                r_state := s_idle
            }
            .otherwise {
                io.count.valid := Bool(true)
            }
        }
    }
}


/** Performs tests on the ThresholdingCompareUnit accumulator.
 *
 * Ensures valid cumulative Hamming weight for randomly generated matrix and
 * threshold elements.
 */
class ThresholdingUnitTests(c: Accumulator) extends Tester(c) {
    var matrix = Array(1, 2, 3, 4)
    var threshold = Array(1, 2, 3)
    var result = Array(1, 2, 3, 3)
    var count = 0
    var running = true

    // Set threshold vector size.
    poke(c.io.size, threshold.size)

    for (i <- 0 to matrix.size - 1) {
        // Set matrix element.
        poke(c.io.matrix.bits, matrix(i))
        poke(c.io.matrix.valid, 1)
        poke(c.io.start, 1)

        // Iterate threshold vector until result is valid.
        var j = 0
        poke(c.io.threshold.bits, threshold(j))
        poke(c.io.threshold.valid, 1)
        while (peek(c.io.count.valid) == 0) {
            if (peek(c.io.threshold.ready) == 1) {
                poke(c.io.threshold.bits, threshold(j))
                j += 1
            }
            step(1)
        }

        expect(c.io.count.bits, result(i))

        // Reset accumulator to idle state.
        poke(c.io.start, 0)
        poke(c.io.matrix.valid, 0)
        poke(c.io.threshold.valid, 0)

        step(1)
    }
}
