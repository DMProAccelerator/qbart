package rosetta

import Chisel._

class CompareUnit extends Module {
    val io = new Bundle {
        val data = SInt(INPUT, width = 16)
        val thresh = SInt(INPUT, width = 16)
        val en = Bool(INPUT)
        val out = UInt(OUTPUT, 1)
    }

    io.out := Bool(false)

    when( io.en ) {
        io.out := io.data >= io.thresh
    }
}
