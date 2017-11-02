package Prototypes

import Chisel._

class ThresholdThreshInput(val n: Int) extends Bundle {
    val in = Vec( n, Bits(INPUT, 8) )
    val en = Vec( n, Bool(INPUT) )

    override def cloneType: this.type = new ThresholdThreshInput(4).asInstanceOf[this.type]
}

class ThresholdElementInput extends Bundle {
    val element = Bits(INPUT, 8)
}

class Threshold(val n: Int) extends Module {
    val io = new Bundle{
        val start = Bool(INPUT)
        val thresh = Decoupled( new ThresholdThreshInput(n) ).flip()
        val in = Decoupled( new ThresholdElementInput() ).flip()
        val out = Valid(Bits(width = 8))
        val cycle = UInt(OUTPUT)

        val state_out = UInt(OUTPUT)
        val element_out = UInt(OUTPUT)
    }

    val s_idle :: s_running :: s_finished :: Nil = Enum(UInt(), 3)
    val state = Reg( init = UInt(s_idle) )

    val check_valid = Reg( init = Bool(false) )
    val element = Reg( UInt(width = 8) )
    val p_element = Reg(init = Bool(true))
    val p_thresh = Reg(init = Bool(true))
    val cycle = Reg( init=UInt(0, width = 8) )

    // Create n comparing units
    val cmp_out = Wire( Vec( n, Bool() ))
    val cmp_units = for (i <- 0 until n ) yield
    {
        val cmp = Module( new CompareUnit() )
        cmp.io.data := element
        cmp.io.thresh := io.thresh.bits.in(i)
        cmp.io.en := io.thresh.bits.en(i)
        cmp_out(i) := cmp.io.out
    }

    val cmp_output = RegNext( cmp_out )

    // Default register values
    check_valid := Bool(false)
    p_thresh := Bool(false)
    cycle := cycle

    // Default OUTPUT values
    io.cycle := cycle
    io.in.ready := !p_element
    io.thresh.ready := !p_thresh
    io.out.bits := UInt(255)
    io.out.valid := Bool(false)

    // DEBUG VALUES
    io.state_out := state
    io.element_out := element

    switch(state) {
        is(s_idle){
            p_thresh := Bool(true)

            when( io.start ) {
                p_thresh := Bool(false)
                p_element := Bool(false)
                cycle := UInt(0)
                state := s_running
            }
        }

        is(s_running) {

            when( check_valid ) {
                when( cmp_out(n - 1) === UInt(0) ) {
                    // When the last comparing unit value is zero, the threshold output is found.
                    state := s_finished
                }
            }

            when( io.in.valid && !p_element ) {
                // There is valid data on the line, and unit is ready to receive.
                element := io.in.bits.element
                p_element := Bool(true)
            }

            when( io.thresh.valid && !p_thresh ) {
                // There are valid thresholds on the line, and unit is ready to receive.
                cycle := cycle + UInt(1)
                p_thresh := Bool(true)
                check_valid := Bool(true)
            }
        }

        is(s_finished) {
            p_thresh := Bool(true)
            io.out.valid := Bool(true)
            io.out.bits := (cycle - UInt(1)) * UInt(n) + PopCount( cmp_output )
            state := s_idle
        }
    }
}


class ThresholdTests(c: Threshold) extends Tester(c) {
    val thresholds = List( 50, 100, 150 )
    val inputs  = List( 180, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val outputs = List(  3,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )

    for ( i <- 0 until inputs.size ) {
        expect( c.io.state_out, 0 )

        poke( c.io.start, 1 )


        step( 10 )
        expect( c.io.state_out, 1 )

        poke( c.io.start, 0 )

        // Give the matrix element
        poke( c.io.in.bits.element, inputs(i) )
        poke( c.io.in.valid, 1 )

        // Give the thresholds
        poke(c.io.thresh.bits.in(0), thresholds(0) )
        poke(c.io.thresh.bits.en(0), 1)

        poke(c.io.thresh.bits.in(1), thresholds(1) )
        poke(c.io.thresh.bits.en(1), 1)

        poke(c.io.thresh.bits.in(2), thresholds(2) )
        poke(c.io.thresh.bits.en(2), 1)

        poke(c.io.thresh.bits.en(3), 0)

        poke(c.io.thresh.valid, 1)

        expect( c.io.out.valid, 0 )
        expect( c.io.cycle, 0 )

        step( 1 )


        poke(c.io.in.valid, 0)
        poke(c.io.thresh.valid, 0)

        // Check outputs

        expect( c.io.state_out, 1 )
        expect( c.io.out.valid, 0 )
        expect( c.io.cycle, 1 )

        step( 1 )

        expect( c.io.element_out, inputs(i) )


        expect( c.io.state_out, 2 )
        expect( c.io.out.valid, 1 )
        expect( c.io.cycle, 1 )

        expect( c.io.out.bits, outputs(i) )

        step( 10 )
    }
}


class ThresholdWithCyclesTests(c: Threshold) extends Tester(c){
    val thresholds = List( 50, 100, 150 )
    val inputs  =   List( 16, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val num_cycles = List(  1,   1,  1, 1,  1,   2,   2,   2,   2,   2,  1,  1,   2 )
    val outputs =   List(  0,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )

    for ( i <- 0 until inputs.size ) {
        // Start the unit
        poke( c.io.start, 1 )

        // while ( peekAt(c.io.in.ready) == 0 && peekAt(c.io.thresh.ready) == 0 ) {
        step(10)
        // }

        // We should expect this to be the first cycle, with invalid output.
        // And that the unit is now ready to receive.
        expect( c.io.cycle, 0 )
        expect( c.io.out.valid, 0 )
        expect( c.io.in.ready, 1 )
        expect( c.io.thresh.ready, 1 )

        poke( c.io.start, 0 )

        // Give the matrix element
        poke( c.io.in.bits.element, inputs(i) )
        poke( c.io.in.valid, 1 )

        // Give the first two thresholds
        poke(c.io.thresh.bits.in(0), thresholds(0) )
        poke(c.io.thresh.bits.en(0), 1)

        poke(c.io.thresh.bits.in(1), thresholds(1) )
        poke(c.io.thresh.bits.en(1), 1)
        poke(c.io.thresh.valid, 1 )

        // while ( c.io.thresh.ready == 0 && c.io.out.valid == 0 ) {
        step(1)
        // }
        expect( c.io.thresh.ready, 0 )
        expect( c.io.out.valid, 0 )

        poke( c.io.thresh.valid, 0 )

        // Data is fed to register, so no need to care about this.
        poke(c.io.in.bits.element, 0)
        poke(c.io.in.valid, 0)

        step(1)


        if ( num_cycles(i) == 2 ) {

            // This should make no difference
            step(10)

            expect( c.io.out.valid, 0 )
            expect( c.io.thresh.ready, 1 )

            // We start our second cycle, one wasn't enough.
            poke( c.io.thresh.bits.in(0), thresholds(2) )
            poke( c.io.thresh.bits.en(0), 1 )

            poke( c.io.thresh.bits.en(1), 0 )

            poke( c.io.thresh.valid, 1 )


            step(1)

            expect( c.io.thresh.ready, 0 )
            expect( c.io.out.valid, 0 )

            poke( c.io.thresh.valid, 0 )

            step(1)
        }

        expect( c.io.out.valid, 1 )
        expect( c.io.out.bits, outputs(i) )
        expect( c.io.cycle, num_cycles(i) )

        step(1)
    }
}
