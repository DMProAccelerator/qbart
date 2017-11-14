package Prototypes
import Chisel._


class ThresholdWrapper extends Module {
    val numMemPorts = 0
    val num_of_cmp_units = 2

    val io = new Bundle {
        val matrix_element = UInt(INPUT, width = 32)
        val thresholds = Vec.fill(4) { UInt(INPUT, width = 32) }
        val start = Bool(INPUT)
        val output = Valid( UInt(OUTPUT, width = 32) )
        val cc = UInt(OUTPUT,  width = 32)
        val finish = Bool(INPUT)

        val state_out = UInt(OUTPUT, width = 32)
    }

    val threshold_unit = Module( new Threshold(num_of_cmp_units) )

    val s_idle :: s_running :: s_finished :: Nil = Enum( UInt(), 3 )
    val r_state = Reg( init = UInt(s_idle) )
    // io.state_out := r_state

    val r_index = Reg( init = UInt(0) )
    val r_output = Reg( init = UInt(255) )
    val r_output_valid = Reg( init = Bool(false) )
    val r_cc = Reg( init = UInt(0) )

    r_cc := r_cc + UInt(1)
    io.cc := r_cc

    // Default OUTPUT values
    io.output.valid := threshold_unit.io.out.valid
    io.output.bits := threshold_unit.io.out.bits

    // Default threshold_unit INPUT values
    threshold_unit.io.start := Bool(false)

    threshold_unit.io.element.bits := io.matrix_element
    threshold_unit.io.element.valid := Bool(false)

    threshold_unit.io.thresh.bits.in(0) := io.thresholds( r_index * UInt(num_of_cmp_units) )
    threshold_unit.io.thresh.bits.en(0) := Bool(true)

    threshold_unit.io.thresh.bits.in(1) := io.thresholds( r_index * UInt(num_of_cmp_units) + UInt(1) )
    threshold_unit.io.thresh.bits.en(1) := Bool(true)

    when( r_index === UInt( num_of_cmp_units - 1 ) ) {
        threshold_unit.io.thresh.bits.en( num_of_cmp_units - 1 ) := Bool(false)
    }

    threshold_unit.io.thresh.valid := Bool(false)
    threshold_unit.io.out.ready := Bool(false)

    // Default register values

    switch( r_state ) {
        is ( s_idle ) {
            when ( io.start ) {
                r_index := UInt(0)
                threshold_unit.io.element.valid := Bool(true)
                threshold_unit.io.start := Bool(true)
                r_output_valid := Bool(false)

                r_state := s_running
            }
        }

        is ( s_running ) {

            r_index := r_index + UInt(1)
            threshold_unit.io.thresh.valid := Bool(true)

            when ( threshold_unit.io.out.valid ) {
                // r_index := r_index - UInt(1)
                r_state := s_finished
                threshold_unit.io.thresh.valid := Bool(false)
            }

        }

        is ( s_finished ) {
            r_index := r_index

            io.output.valid := Bool(true)

            when (io.finish) {
                threshold_unit.io.out.ready := Bool(true)
                r_state := s_idle
            }
        }
    }
}



class ThresholdWrapperTests (c: ThresholdWrapper) extends Tester(c) {
    val thresholds = List( 50, 100, 150 )
    val inputs  =   List( 16, 28, 40, 52, 89, 128, 175, 228, 255, 140, 80, 20, 180 )
    val num_cycles = List( 1,  1,  1,  1,  1,   2,   2,   2,   2,   2,  1,  1,   2 )
    val outputs =   List(  0,  0,  0,  1,  1,   2,   3,   3,   3,   2,  1,  0,   3 )


    for (i <- 0 until inputs.size) {
        poke( c.io.finish, 0 )
        poke( c.io.matrix_element, inputs(i) )
        poke( c.io.thresholds(0), thresholds(0) )
        poke( c.io.thresholds(1), thresholds(1) )
        poke( c.io.thresholds(2), thresholds(2) )

        poke( c.io.start, 1 )

        step(10)

        expect( c.io.output.valid, 1 )
        expect( c.io.output.bits, outputs(i) )


        poke( c.io.start, 0 )
        poke( c.io.finish, 1 )

        step(100)
    }






}
