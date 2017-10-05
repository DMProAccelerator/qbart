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
        }
    }
}
