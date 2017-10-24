package Prototypes

import Chisel._

class ThresholdThreshInput(val n: Int) extends Bundle {
    val in = Vec( n, Bits(INPUT, 8) )
    val en = Vec( n, Bits(INPUT, 1) )

    override def cloneType: this.type = new ThresholdThreshInput(4).asInstanceOf[this.type]
}

class ThresholdDataInput extends Bundle {
    val data = Bits(INPUT, 8)
}

class Threshold(val n: Int) extends Module {
    val io = new Bundle{
        val reset = Bool(INPUT)
        val thresh = Decoupled( new ThresholdThreshInput(n) ).flip()
        val in = Decoupled( new ThresholdDataInput() ).flip()
        val out = Valid(Bits(width = 8))
        val cycle = UInt(OUTPUT)
    }

    io.out.bits := UInt(255)
    io.out.valid := Bool(false)

    val data = Reg( UInt(width = 8) )
    val cycle = Reg( init=UInt(0, width = 8) )
    cycle := cycle + UInt(1)
    io.cycle := cycle


    when ( io.reset ) {
        // Cycle will be 0 in the next cycle.
        cycle := UInt(0)
        io.out.bits := UInt(1)
    }

    val p_data = Bool(false)
    val p_thresh = Bool(false)
    val cmp_out = Wire( Vec( n, Bool() ))
    io.in.ready := !p_data

    val cmp_units = for (i <- 0 until n ) yield
    {
        val cmp = Module( new CompareUnit() )
        cmp.io.data := data
        cmp.io.thresh := io.thresh.bits.in(i)
        cmp.io.en := io.thresh.bits.en(i)
        cmp_out(i) := cmp.io.out
    }


    when( io.in.valid && !p_data ) {
        // There is valid data on the line, and unit is ready to receive.
        data := io.in.bits.data
        p_data := Bool(true)
    }

    when( io.thresh.valid && !p_thresh ) {
        // There are valid thresholds on the line, and unit is ready to receive.
        io.out.valid := !cmp_out(n - 1)
        io.out.bits := (cycle) * UInt(n) + PopCount( cmp_out )
        p_thresh := Bool(true)
    }
}



class ThresholdTests(c: Threshold) extends Tester(c) {
    val thresholds = List( 50, 100, 150 )
    val inputs  = List( 180, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val outputs = List(  3,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )

    for ( i <- 0 until inputs.size ) {
        poke( c.io.reset, 1 )

        // Give the unit the data
        poke( c.io.in.bits.data, inputs(i) )
        poke( c.io.in.valid, 1 )

        // Give it the thresholds
        poke(c.io.thresh.bits.in(0), 50)
        poke(c.io.thresh.bits.en(0), 1)

        poke(c.io.thresh.bits.in(1), 100)
        poke(c.io.thresh.bits.en(1), 1)

        poke(c.io.thresh.bits.in(2), 150)
        poke(c.io.thresh.bits.en(2), 1)

        poke(c.io.thresh.bits.en(3), 0)

        poke(c.io.thresh.valid, 1)

        step(1)
        poke( c.io.reset, 0 )

        expect( c.io.out.valid, 1 )
        expect( c.io.out.bits, outputs(i) )
    }


}


class ThresholdWithCyclesTests(c: Threshold) extends Tester(c){
    val thresholds = List( 50, 100, 150 )
    val inputs  =   List( 16, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val one_cycle = List(  1,   1,  1, 1,  1,   0,   0,   0,   0,   0,  1,  1,   0 )
    val outputs =   List(  0,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )

    for ( i <- 0 until inputs.size ) {
        // Expect that the unit is ready to receive
        expect( io.in.ready, 1 )

        // Give the data
        poke( c.io.reset, 1 )
        poke( c.io.in.bits.data, inputs(i) )
        poke( c.io.in.valid, 1 )

        // Give two thresholds
        poke( c.io.thresh.bits.in(0), thresholds(0) )
        poke( c.io.thresh.bits.en(0), 1 )

        poke( c.io.thresh.bits.in(1), thresholds(1) )
        poke( c.io.thresh.bits.en(1), 1 )
        poke( c.io.thresh.valid, 1 )

        // Next cycle
        step(1)
        poke( c.io.reset, 0 )
        poke( c.io.in.valid, 0 )

        if ( one_cycle(i) == 1 ) {
            expect( c.io.out.valid, 1 )
            expect( c.io.out.bits, outputs(i) )
        }
        else {
            expect( c.io.out.valid, 0 )
            expect( c.io.in.ready, 0 )

            // Give last threshold
            poke( c.io.thresh.bits.in(0), thresholds(2) )
            poke( c.io.thresh.bits.en(0), 1 )

            poke( c.io.thresh.bits.en(1), 0 )

            // Next cycle
            step(1)
            expect( c.io.out.valid, 1 )
            expect( c.io.out.bits, outputs(i) )
        }
    }
}
