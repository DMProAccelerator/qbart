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
        val params = args.slice(1, args.length)
        args(0) match {
            // Add modules for testing.
        }
    }
}
