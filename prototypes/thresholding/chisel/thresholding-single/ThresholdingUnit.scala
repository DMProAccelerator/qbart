package Prototypes

import Chisel._


/** Compares two values and accumulates their corresponding Hamming weight.
 *
 * The class provides a hard step function that compares a matrix element
 * M(i, j) to a threshold element T(k). If M(i, j) >= T(k), then the Hamming
 * weight is incremented by one.
 */
class ThresholdingUnit extends Module {
    val io = new Bundle {
        val matrix = Decoupled(UInt(INPUT, width = 32)).flip()
        val size = UInt(INPUT, width = 32)
        val op = Vec.fill(255) { UInt(INPUT, width = 32) }
        val start = Bool(INPUT)
        val count = Decoupled(UInt(OUTPUT, width = 32))
    }

    val s_idle :: s_running :: s_finished :: Nil = Enum(UInt(), 3)

    val r_state = Reg(init = s_idle)
    val r_accumulator = Reg(init = UInt(0, 32))
    val r_index = Reg(init = UInt(0, 32))

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


/** Performs tests on the thresholding unit.
 *
 * Ensures valid cumulative Hamming weight for randomly generated matrix and
 * threshold elements.
 */
class ThresholdingUnitTests(c: ThresholdingUnit) extends Tester(c) {
    // Domain sizes. Currently set for 128 x 128 matrix and 4-bit thresholding
    // vector.
    var SIZE_MATRIX = 128 * 128
    var SIZE_THRESH = 16

    var matrix:Array[Integer] = Array.fill(SIZE_MATRIX) (rnd.nextInt(255))
    var threshold:Array[Integer] = Array.fill(SIZE_THRESH)(rnd.nextInt(255))
    var result:Array[Integer] = new Array[Integer](SIZE_MATRIX)

    // Calculate results.
    for (i <- 0 to matrix.size - 1) {
        var x = matrix(i)
        var count = 0
        for (t <- threshold) {
            count += (if (x >= t) 1 else 0)
        }
        result(i) = count
    }

    // Prepare threshold vector.
    for (i <- 0 to threshold.size - 1) {
        poke(c.io.op(i), threshold(i))
    }
    poke(c.io.size, threshold.size)

    for (i <- 0 to matrix.size - 1) {
        // Set matrix element.
        poke(c.io.matrix.bits, matrix(i))
        poke(c.io.matrix.valid, 1)
        poke(c.io.start, 1)

        // Iterate threshold vector until valid result.
        while (peek(c.io.count.valid) == 0) {
            step(1)
        }

        expect(c.io.count.bits, result(i))

        // Reset accumulator to idle state.
        poke(c.io.start, 0)
        poke(c.io.matrix.valid, 0)

        step(1)
    }
}
