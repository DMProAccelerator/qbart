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
        val write_enable = Bool(INPUT)
        val reset = Bool(INPUT)
        val count = Decoupled(UInt(OUTPUT, width = 32))
    }

    val accumulator_r = Reg(init = UInt(0, 32))

    io.count.bits := accumulator_r
    io.count.valid := Bool(false)
    io.matrix.ready := Bool(false)
    io.threshold.ready := Bool(false)

    when (io.write_enable & !io.reset) {
        when (io.matrix.valid & io.threshold.valid) {
            accumulator_r := accumulator_r +
                PopCount.compare(io.matrix.bits, io.threshold.bits)
            io.count.valid := Bool(true)
            io.matrix.ready := Bool(true)
            io.threshold.ready := Bool(true)
        }
    }

    when (io.reset) {
        accumulator_r := UInt(0)
    }

    io.count.bits := accumulator_r
}


/** Performs tests on the ThresholdingCompareUnit accumulator.
 *
 * Ensures valid cumulative Hamming weight for randomly generated matrix and
 * threshold elements.
 */
class ThresholdingUnitTests(c: Accumulator) extends Tester(c) {
    // Accumulator value.
    var count = 0
    // Threshold vector size. Currently set for 8-bit thresholding.
    var size = 30
    // Number of matrix elements to perform thresholding on.
    var elements = 1
    // Single matrix element.
    var matrix = 0
    // Single thresholding element.
    var threshold = 0

    // Clear accumulator.
    poke(c.io.reset, 1)
    poke(c.io.write_enable, 0);
    step(1)

    // Set default state.
    poke(c.io.write_enable, 1)
    poke(c.io.reset, 0)

    // Start testing.
    for (i <- 1 to (elements * size) ) {
        matrix = rnd.nextInt(255)
        threshold = rnd.nextInt(255)

        if (matrix >= threshold) {
            count = count + 1
        }

        // Flush accumulator.
        if (i % size == 0) {
            poke(c.io.reset, 1)
            count = 0
        }

        poke(c.io.matrix.bits, matrix)
        poke(c.io.threshold.bits, threshold)
        poke(c.io.matrix.valid, 1)
        poke(c.io.threshold.valid, 1)
        step(1)
        expect(c.io.count.bits, count)

        // Return to default state.
        if (i % size == 0) {
            poke(c.io.reset, 0)
        }
    }
}
