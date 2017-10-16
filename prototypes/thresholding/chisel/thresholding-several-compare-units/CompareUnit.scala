package Prototypes

import Chisel._

class CompareUnit extends Module {
    val io = new Bundle {
        val data = Bits(INPUT, width = 8)
        val thresh = Bits(INPUT, width = 8)
        val en = Bool(INPUT)
        val out = UInt(OUTPUT, 1)
    }

    io.out := Bool(false)

    when( io.en ) {
        io.out := (UInt(io.data) >= UInt(io.thresh))
    }
}


class CompareUnitTests(c: CompareUnit) extends Tester(c) {
    val inputs = List( 3, 4, 5 )
    val thresh = 4
    val outputs = List( 0, 1, 1 )

    for (en <- 0 to 1) {
        for (i <- 0 until 3 ) {
            poke(c.io.thresh, thresh)
            poke(c.io.data, inputs(i))
            poke(c.io.en, en)

            expect(c.io.out, outputs(i) & en)
        }
    }
}
