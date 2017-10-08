package Prototypes

import Chisel._

class ThresholdThreshInput extends Bundle {
    val t1    = Bits(INPUT, 8)
    val t1_en = Bits(INPUT, 1)

    val t2    = Bits(INPUT, 8)
    val t2_en = Bits(INPUT, 1)

    val t3    = Bits(INPUT, 8)
    val t3_en = Bits(INPUT, 1)

    val t4    = Bits(INPUT, 8)
    val t4_en = Bits(INPUT, 1)
}

class ThresholdDataInput extends Bundle {
    val data = Bits(INPUT, 8)
}

class Threshold extends Module {
    val io = new Bundle{
        val thresh = Decoupled( new ThresholdThreshInput() ).flip()
        val in = Decoupled( new ThresholdDataInput() ).flip()
        val out = Valid(Bits(width = 8))
    }

    io.out.bits := UInt(255)
    io.out.valid := Bool(false)

    val data = Reg( UInt(width = 8) )
    val cmp1 = Module( new CompareUnit() )
    cmp1.io.data    := data
    cmp1.io.thresh  := io.thresh.bits.t1
    cmp1.io.en      := io.thresh.bits.t1_en

    val cmp2 = Module(new CompareUnit())
    cmp2.io.data    := data
    cmp2.io.thresh  := io.thresh.bits.t2
    cmp2.io.en      := io.thresh.bits.t2_en

    val cmp3 = Module(new CompareUnit())
    cmp3.io.data    := data
    cmp3.io.thresh  := io.thresh.bits.t3
    cmp3.io.en      := io.thresh.bits.t3_en

    val cmp4 = Module(new CompareUnit())
    cmp4.io.data    := data
    cmp4.io.thresh  := io.thresh.bits.t4
    cmp4.io.en      := io.thresh.bits.t4_en

    val p_data = Bool(false)
    val p_thresh = Bool(false)
    val cmp_out = Wire( Vec( 4, Bool() ))
    io.in.ready := !p_data

    cmp_out(0) := cmp1.io.out
    cmp_out(1) := cmp2.io.out
    cmp_out(2) := cmp3.io.out
    cmp_out(3) := cmp4.io.out

    when( io.in.valid && !p_data ) {
        // There is valid data on the line, and unit is ready to receive.
        data := io.in.bits.data
        p_data := Bool(true)
    }

    when( io.thresh.valid && !p_thresh ) {
        // There are valid thresholds on the line, and unit is ready to receive.
        p_thresh := Bool(true)
        io.out.valid := Bool(true)
        io.out.bits := PopCount( cmp_out )
        p_thresh := Bool(false)
    }
}



class ThresholdTests(c: Threshold) extends Tester(c) {
    val thresholds = List( 50, 100, 150 )
    val inputs  = List( 16, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val outputs = List(  0,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )

    for ( i <- 0 until inputs.size ) {
        // Give the unit the data
        poke( c.io.in.bits.data, inputs(i) )
        poke( c.io.in.valid, 1 )

        // Give it the thresholds
        poke(c.io.thresh.bits.t1, 50)
        poke(c.io.thresh.bits.t1_en, 1)

        poke(c.io.thresh.bits.t2, 100)
        poke(c.io.thresh.bits.t2_en, 1)

        poke(c.io.thresh.bits.t3, 150)
        poke(c.io.thresh.bits.t3_en, 1)

        poke(c.io.thresh.bits.t4_en, 0)

        poke(c.io.thresh.valid, 1)

        step(1)

        expect( c.io.out.valid, 1 )
        expect( c.io.out.bits, outputs(i) )
    }


}
