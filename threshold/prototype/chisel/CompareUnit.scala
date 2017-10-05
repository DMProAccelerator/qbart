package TutorialSolutions

import Chisel._

class CompareUnitInput extends Bundle {
    val a = Bits(width = 8)    // Threshold input
    val b = Bits(width = 8)    // data input
}

class CompareUnit extends Module {
    val io = new Bundle {
        val in = Decoupled( new CompareUnitInput() ).flip()
        val out = UInt(OUTPUT, 1)
    }

    val thresh = Reg(UInt(width = 8))
    val data = Reg(UInt(width = 8))
    val p = Bool(false)
    io.in.ready := !p
    io.out := Bool(false)

    when( io.in.valid && !p ) {
        thresh := io.in.bits.a
        data := io.in.bits.b
        p := Bool(true)

        io.out := (data >= thresh)
        p := Bool(false)
    }
}


class CompareUnitTests(c: CompareUnit) extends Tester(c) {
    val inputs = List( (4, 3), (4, 4), (4, 5) )    // (data, threshold)
    val outputs = List( 0, 1, 1 )

    for (en <- 0 to 1) {
        for (i <- 0 until 3 ) {
            poke(c.io.in.bits.a, inputs(i)._1)
            poke(c.io.in.bits.b, inputs(i)._2)
            poke(c.io.in.valid, en)

            step(1)

            expect(c.io.out, outputs(i) & en)
        }
    }
}
