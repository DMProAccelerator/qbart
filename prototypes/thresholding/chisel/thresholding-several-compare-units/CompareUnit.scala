package Prototypes

import Chisel._

class CompareUnit extends Module {
    val io = new Bundle {
        val data = SInt(INPUT, width = 16)
        val thresh = SInt(INPUT, width = 16)
        val en = Bool(INPUT)
        val out = UInt(OUTPUT, 1)
    }

    io.out := Bool(false)

    when( io.en ) {
        io.out := io.data >= io.thresh
    }
}


class CompareUnitTests(c: CompareUnit) extends Tester(c) {
    var inputs = List( 3, 4, 5, -2 )
    var thresh = 4
    var outputs = List( 0, 1, 1, 0 )

    for (en <- 0 to 1) {
        for (i <- 0 until inputs.size) {
            poke(c.io.thresh, thresh)
            poke(c.io.data, inputs(i))
            poke(c.io.en, en)

            expect(c.io.out, outputs(i) & en)
        }
    }

    inputs = List( -5,-8, 3, -1 )
    thresh = -1
    outputs = List( 0, 0, 1, 1 )

    for (en <- 0 to 1) {
        for (i <- 0 until inputs.size) {
            poke(c.io.thresh, thresh)
            poke(c.io.data, inputs(i))
            poke(c.io.en, en)

            expect(c.io.out, outputs(i) & en)
        }
    }
}
