package rosetta

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
