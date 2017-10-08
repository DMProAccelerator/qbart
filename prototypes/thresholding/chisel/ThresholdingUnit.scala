package Prototypes

import Chisel._


/**
 * Compares two values and accumulates their corresponding Hamming weight.
 *
 * Input  1: Matrix element.
 * Input  2: Threshold element.
 * Output 1: Cumulative Hamming weight.
 * Output 2: Count-value indicating how many times this module has been applied
 *           on a single matrix element.
 *
 * The class provides a hard step function that compares a matrix element
 * M(i, j) to a threshold element T(k). If M(i, j) >= T(k), then the Hamming
 * weight is incremented by one.
 */
class ThresholdingCompareUnit extends Module {

    // Setup I/O.
    val io = new Bundle {
        val matrix_element = UInt(INPUT, 32)
        val threshold_element = UInt(INPUT, 32)
        val hamming_weight = UInt(OUTPUT, 32)
        val count = UInt(OUTPUT, 32)
    }

    // Allocate registers and step function.
    val accumulator_r = Reg(init = UInt(0, 32))
    val count_r = Reg(init = UInt(0, 32))
    val step = UInt(width = 1)

    // Apply step function.
    step := UInt(0)
    when (io.matrix_element >= io.threshold_element) {
        step := UInt(1)
    }

    // Accumulate and output results.
    accumulator_r := accumulator_r + step
    count_r := count_r + UInt(1)
    io.hamming_weight := accumulator_r
    io.count := count_r

}


/**
 * Performs tests on the ThresholdingCompareUnit accumulator. Ensures valid
 * cumulative Hamming weight for randomly generated matrix and threshold
 * elements.
 */
class ThresholdingUnitTests(c: ThresholdingCompareUnit) extends Tester(c) {

    var matrix_element_t = 0
    var threshold_element_t = 0
    var hamming_weight_t = 0
    var count_t = 0
    var threshold_length = 4

    for (_ <- 1 to threshold_length) {
        // Generate random test-values.
        matrix_element_t = rnd.nextInt(Int.MaxValue)
        threshold_element_t = rnd.nextInt(Int.MaxValue)

        // Apply step function.
        var step_t = 0
        if (matrix_element_t >= threshold_element_t) {
            step_t = 1
        }

        // Accumulate result.
        hamming_weight_t += step_t
        count_t += 1

        // Set input and compare results.
        poke(c.io.matrix_element, matrix_element_t)
        poke(c.io.threshold_element, threshold_element_t)
        step(1)
        expect(c.io.hamming_weight, hamming_weight_t)
        expect(c.io.count, count_t)
    }

}
