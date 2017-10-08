package Prototypes

import Chisel._

object Prototypes {
    def main(args: Array[String]): Unit = {
        val tutArgs = args.slice(1, args.length)
        args(0) match {
            case "ThresholdingCompareUnit" =>
                chiselMainTest(tutArgs, () => Module(new ThresholdingCompareUnit())) {
                    c => new ThresholdingUnitTests(c)
                }
        }
    }
}
