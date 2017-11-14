package Prototypes

import Chisel._

/** Loads and executes module tests.
 *
 * A module is added to the loader the following way:
 *
 * case "<module-name>" =>
 *     chiselMainTest(params, () => Module(new <module-name>())) {
 *         c = new <module-test-name>(c) }
 *
 * All modules contained in the build directory are compiled and lodaded during
 * testing.
 */

object Prototypes {
    def main(args: Array[String]): Unit = {
        val tutArgs = args.slice(1, args.length)
        args(0) match {
            case "Adder" =>
                chiselMainTest(tutArgs, () => Module(new Adder())) {
                    c => new AdderTests(c)
                }

            case "CompareUnit" =>
                chiselMainTest(tutArgs, () => Module(new CompareUnit())) {
                    c => new CompareUnitTests(c)
                }

            case "ThresholdOneCycle" =>
                chiselMainTest(tutArgs, () => Module(new Threshold(4))) {
                    c => new ThresholdTests(c)
                }

            case "ThresholdSeveralCycles" =>
                chiselMainTest(tutArgs, () => Module(new Threshold(2))) {
                    c => new ThresholdWithCyclesTests(c)
                }

            case "ThresholdWrapper" =>
                chiselMainTest(tutArgs, () => Module(new ThresholdWrapper())) {
                    c => new ThresholdWrapperTests(c)
                }
        }
    }
}
