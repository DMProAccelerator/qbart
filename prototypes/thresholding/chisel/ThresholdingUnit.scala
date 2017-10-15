package Prototypes

import Chisel._


/** Compares two values and accumulates their corresponding Hamming weight.
 *
 * The class provides a hard step function that compares a matrix element
 * M(i, j) to a threshold element T(k). If M(i, j) >= T(k), then the Hamming
 * weight is incremented by one.
 */
class ThresholdingCompareUnit extends Module {
    val io = new Bundle {
        val matrix = UInt(INPUT, width = 32)
        val threshold = UInt(INPUT, width = 32)
        val write_enable = Bool(INPUT)
        val reset = Bool(INPUT)
        val count = UInt(OUTPUT, width = 32)
    }

    val accumulator_r = Reg(init = UInt(0, 32))

    when (io.write_enable & !io.reset) {
        when (io.matrix >= io.threshold) {
            accumulator_r := accumulator_r + UInt(1)
        }
    }

    when (io.reset) {
        accumulator_r := UInt(0)
    }

    io.count := accumulator_r
}


/** Performs tests on the ThresholdingCompareUnit accumulator.
 *
 * Ensures valid cumulative Hamming weight for randomly generated matrix and
 * threshold elements.
 */
class ThresholdingUnitTests(c: ThresholdingCompareUnit) extends Tester(c) {
    // Accumulator value.
    var count = 0
    // Threshold vector size. Currently set for 8-bit thresholding.
    var size = 7
    // Number of matrix elements to perform thresholding on.
    var elements = 255
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

        poke(c.io.matrix, matrix)
        poke(c.io.threshold, threshold)
        step(1)
        expect(c.io.count, count)

        // Return to default state.
        if (i % size == 0) {
            poke(c.io.reset, 0)
        }
    }
}
