package Prototypes

import Chisel._

class Adder extends Module {
    val io = new Bundle {
        val in0 = UInt(INPUT,  1)
        val in1 = UInt(INPUT,  1)
        val out = UInt(OUTPUT, 1)
    }
    io.out := io.in0 + io.in1
}

class AdderTests(c: Adder) extends Tester(c) {
    val in0 = 1
    val in1 = 2
    poke(c.io.in0, in0)
    poke(c.io.in1, in1)
    step(1)
    expect(c.io.out, in0 + in1)
}
