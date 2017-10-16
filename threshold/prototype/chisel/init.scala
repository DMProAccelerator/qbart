package Prototypes

import Chisel._

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

            case "Threshold4" =>
                chiselMainTest(tutArgs, () => Module(new Threshold(4))) {
                    c => new ThresholdTests(c)
                }

            case "Threshold2" =>
                chiselMainTest(tutArgs, () => Module(new Threshold(2))) {
                    c => new ThresholdWithCyclesTests(c)
                }
        }
    }
}
