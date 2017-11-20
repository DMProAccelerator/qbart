package rosetta

import Chisel._

class ThresholdThreshInput(val n: Int) extends Bundle {
    val in = Vec( n, SInt(INPUT, width = 64) )
    val en = Vec( n, Bool(INPUT) )

    override def cloneType: this.type = new ThresholdThreshInput(64).asInstanceOf[this.type]
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
        val element = Decoupled( SInt(width = 64) ).flip()
        val out = Decoupled( UInt(width = 64) )
        val cycle = UInt(OUTPUT)

        // DEBUG
        val state_out = UInt(OUTPUT)
        val matrix_out = UInt(OUTPUT)
    }

    val s_idle :: s_running :: s_finished :: Nil = Enum(UInt(), 3)
    val r_state = Reg( init = UInt(s_idle) )

    val r_element = Reg( SInt(width = 64) )
    val r_element_ready = Reg(init = Bool(true))
    val r_thresh_ready = Reg(init = Bool(false))
    val r_cycle = Reg( init=UInt(0, width = 8) )

    val r_output = Reg( init = UInt(255, width = 64) )
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

    // DEBUG
    io.state_out := r_state
    io.matrix_out := r_element

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
                r_output_valid := Bool(false)
                r_element_ready := Bool(true)
                r_state := s_idle
            }
        }
    }
}
