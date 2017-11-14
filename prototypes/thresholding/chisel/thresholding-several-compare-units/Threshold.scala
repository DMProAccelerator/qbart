package Prototypes

import Chisel._

class ThresholdThreshInput(val n: Int) extends Bundle {
    val in = Vec( n, UInt(INPUT, width = 8) )
    val en = Vec( n, Bool(INPUT) )

    override def cloneType: this.type = new ThresholdThreshInput(4).asInstanceOf[this.type]
}

/** The Use of Threshold:

1.  Give the element
2.  Set the start signal. The element must have been given before or
    in the same cycle as the start signal is set.
    The unit will begin in the next cycle.
3.  As long the out.valid is 0, you can give the next batch of thresholds.
4.  When out.valid is 1, the result can be read from out.bits. Set out.ready
    when reading the output.

- Make sure the thresh.valid, element.valid and out.ready is set only during
  the cycle when transmission of data occurs, or else it might lead to undefined behavior.
  Especially with thresh.valid: Clear the signal in the next cycle if a new batch of
  thresholds won't be transferred.
- The threshold values must be driven at all times.
- The last threshold values must be driven until the final output is read.
- Use thresh.ready and element.ready to control when data can be transferred
  to the unit.

*/

class Threshold(val n: Int) extends Module {
    val io = new Bundle{
        val start = Bool(INPUT)
        val thresh = Decoupled( new ThresholdThreshInput(n) ).flip()
        val element = Decoupled( UInt(width = 8) ).flip()
        val out = Decoupled( UInt(width = 8) )
        val cycle = UInt(OUTPUT)

        val state_out = UInt(OUTPUT)
    }

    val s_idle :: s_running :: s_finished :: Nil = Enum(UInt(), 3)
    val r_state = Reg( init = UInt(s_idle) )

    val r_element = Reg( UInt(width = 8) )
    val r_element_ready = Reg(init = Bool(true))
    val r_thresh_ready = Reg(init = Bool(false))
    val r_cycle = Reg( init=UInt(0, width = 8) )

    val r_output = Reg( init = UInt(255) )
    val r_output_valid = Reg( init = Bool(false) )

    // Create n comparing units
    val cmp_out = Wire( Vec( n, Bool() ))
    val cmp_units = for (i <- 0 until n ) yield
    {
        val cmp = Module( new CompareUnit() )
        cmp.io.data := r_element
        cmp.io.thresh := io.thresh.bits.in(i)
        cmp.io.en := io.thresh.bits.en(i)
        cmp_out(i) := cmp.io.out
    }

    // Default register values
    r_cycle := r_cycle
    r_output_valid := Bool(false)

    // Default OUTPUT values
    io.cycle := r_cycle
    io.element.ready := r_element_ready
    io.thresh.ready := r_thresh_ready
    io.out.bits := r_output
    io.out.valid := r_output_valid

    // DEBUG VALUES
    io.state_out := r_state

    switch(r_state) {
        is(s_idle){

            when( io.element.valid && r_element_ready ) {
                // There is valid data on the line, and unit is ready to receive.
                r_element := io.element.bits
                r_element_ready := Bool(false)
            }

            when( io.start ) {
                r_thresh_ready := Bool(true)
                r_cycle := UInt(0)
                r_state := s_running
            }
        }

        is(s_running) {

            when( io.thresh.valid && r_thresh_ready ) {
                // There are valid thresholds on the line, and unit is ready to receive.
                r_cycle := r_cycle + UInt(1)

                when( cmp_out(n - 1) === UInt(0) ) {
                    // When the last comparing unit value is zero, the threshold output is found.
                    r_output_valid := Bool(true)
                    r_output := r_cycle * UInt(n) + PopCount( cmp_out )
                    r_cycle := r_cycle

                    r_thresh_ready := Bool(false)
                    r_state := s_finished
                }
            }
        }

        is(s_finished) {
            r_output := r_output
            r_output_valid := Bool(true)

            when( io.out.ready ) {
                r_element_ready := Bool(true)
                r_state := s_idle
            }
        }
    }
}


class ThresholdTests(c: Threshold) extends Tester(c) {
    val thresholds = List( 50, 100, 150 )
    val inputs  = List( 180, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val outputs = List(  3,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )

    for ( i <- 0 until inputs.size ) {

        // State s_idle
        // Give the matrix element and start it
        poke( c.io.element.bits, inputs(i) )
        poke( c.io.element.valid, 1 )
        poke( c.io.start, 1 )
        poke(c.io.out.ready, 0)

        step( 1 )

        // State s_running, unit should be ready to receive thresholds
        expect(c.io.thresh.ready, 1)

        poke( c.io.element.valid, 0 )
        poke( c.io.start, 0 )

        // Give the thresholds
        poke(c.io.thresh.bits.in(0), thresholds(0) )
        poke(c.io.thresh.bits.en(0), 1)

        poke(c.io.thresh.bits.in(1), thresholds(1) )
        poke(c.io.thresh.bits.en(1), 1)

        poke(c.io.thresh.bits.in(2), thresholds(2) )
        poke(c.io.thresh.bits.en(2), 1)

        poke(c.io.thresh.bits.en(3), 0)

        poke(c.io.thresh.valid, 1)

        step( 1 )

        // State s_finished, output should be valid and output bits correct.
        expect( c.io.out.valid, 1 )
        expect( c.io.out.bits, outputs(i) )

        poke( c.io.out.ready, 1 )

        step( 1 )

    }
}


class ThresholdWithCyclesTests(c: Threshold) extends Tester(c){
    val thresholds = List( 50, 100, 150 )
    val inputs  =   List( 16, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val num_cycles = List(  1,   1,  1, 1,  1,   2,   2,   2,   2,   2,  1,  1,   2 )
    val outputs =   List(  0,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )

    for ( i <- 0 until inputs.size ) {
        expect( c.io.state_out, 0 )
        expect( c.io.element.ready, 1 )

        // Give the matrix element
        poke( c.io.element.bits, inputs(i) )
        poke( c.io.element.valid, 1 )

        // Start the unit
        poke( c.io.start, 1 )

        // while ( peekAt(c.io.element.ready) == 0 && peekAt(c.io.thresh.ready) == 0 ) {
        step(10)
        // }

        // Data has been fed to the register in the unit, so no need to care about this.
        poke(c.io.element.bits, 0)
        poke(c.io.element.valid, 0)

        // We should expect this to be the first cycle, with invalid output.
        // And that the unit is now ready to receive.
        expect( c.io.cycle, 0 )
        expect( c.io.out.valid, 0 )
        expect( c.io.thresh.ready, 1 )

        poke( c.io.start, 0 )

        // Give the first two thresholds
        poke(c.io.thresh.bits.in(0), thresholds(0) )
        poke(c.io.thresh.bits.en(0), 1)

        poke(c.io.thresh.bits.in(1), thresholds(1) )
        poke(c.io.thresh.bits.en(1), 1)
        poke(c.io.thresh.valid, 1 )

        step(1)

        // Clear the valid-signal in the next cycle - IMPORTANT
        poke(c.io.thresh.valid, 0)

        // This should make no difference
        step(10)

        if ( num_cycles(i) == 2 ) {

            expect( c.io.out.valid, 0 )
            expect( c.io.thresh.ready, 1 )

            // We start our second cycle, one wasn't enough.
            poke( c.io.thresh.bits.in(0), thresholds(2) )
            poke( c.io.thresh.bits.en(0), 1 )

            poke( c.io.thresh.bits.en(1), 0 )

            poke( c.io.thresh.valid, 1 )


            step(1)

            poke( c.io.thresh.valid, 0 )

            step(10)
        }

        poke( c.io.out.ready, 1 )
        expect( c.io.out.valid, 1 )
        expect( c.io.out.bits, outputs(i) )
        // expect( c.io.cycle, num_cycles(i) )

        step(1)
        poke( c.io.out.ready, 0 )
    }
}
