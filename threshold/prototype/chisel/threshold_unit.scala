
import chisel._

class Compare_input extends Module {
    val a = Bits(Input, width=8)    // Threshold input
    val b = Bits(Input, width=8)    // data input
}

class Compare extends Module{
    val io = new Bundle {
        val in      = Decoupled(new Compare_input()).flip()
        val out     = Bool(OUTPUT)
    }

    val thresh = Reg(Uint, width=8)
    val data = Reg(Uint, width=8)
    val p = Reg(init=false)
    io.in.ready := !p

    when( io.in.valid && !p ) {
        thresh := io.in.bits.a
        data := io.in.bits.b
        p := Bool(true)

    }
}
