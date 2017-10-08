package Prototypes

import Chisel._


/**
 * Compares two values and accumulates their corresponding Hamming distance.
 *
 * Input  1: Matrix element.
 * Input  2: Threshold element.
 * Input  3: Reset bit indicating register flushing.
 * Output 1: Cumulative hamming distance.
 * Output 2: Count-value indicating how many times this module has been applied
 *           on a single matrix element.
 *
 * The class provides a hard step function that compares a matrix element
 * M(i, j) to a threshold element T(k). If M(i, j) >= T(k), then the hamming
 * distance is incremented by one.
 */
class ThresholdingCompareUnit extends Module {

    // Setup I/O.
    val io = new Bundle {
        val matrix_element = UInt(INPUT, 32)
        val threshold_element = UInt(INPUT, 32)
        val reset = UInt(INPUT, 1)
        val hamming_weight = UInt(OUTPUT, 32)
        val count = UInt(OUTPUT, 32)
    }

    // Allocate registers and step function.
    val count_r = Reg(init = UInt(0, 32))
    val accumulator_r = Reg(init = UInt(0, 32))
    val step = UInt(width = 1)

    // Flush all registers when reset bit is set.
    when (reset) {
        accumulator_r := UInt(0)
        count_r := UInt(0)
    }

    // Apply step function.
    step := UInt(0)
    when (io.matrix_element >= io.threshold_element) {
        step := UInt(1)
    }

    // Accumulate results.
    accumulator_r := accumulator_r + step
    count_r := count_r + UInt(1)

    // Output results.
    io.count := count_r
    io.hamming_weight := accumulator_r

}

class ThresholdingUnitTests(c: ThresholdingCompareUnit) extends Tester(c) {

    var matrix_element_t = 0
    var threshold_element_t = 0
    var hamming_weight_t = 0
    var reset_t = 0
    var count_t = 0

    for (_ <- 0 to 3) {
        // Generate random test values.
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
        poke(c.io.reset, reset_t)
        step(1)
        expect(c.io.hamming_weight, hamming_weight_t)
        expect(c.io.count, count_t)
    }

}
