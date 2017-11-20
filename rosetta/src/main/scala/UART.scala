/*
 * Copyright: 2014-2017, Technical University of Denmark, DTU Compute
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * License: Simplified BSD License
 * 
 * A UART is a serial port, also called an RS232 interface.
 * 
 */

package rosetta

import Chisel._
import fpgatidbits.PlatformWrapper._


class Channel extends Bundle {
  val data = Bits(INPUT, 8)
  val ready = Bool(OUTPUT)
  val valid = Bool(INPUT)
}

/**
 * Transmit part of the UART.
 * A minimal version without any additional buffering.
 * Use an AXI like valid/ready handshake.
 */
class Tx(frequency: Int, baudRate: Int) extends Module {
  val io = new Bundle {
    val txd = Bits(OUTPUT, 1)
    val channel = new Channel()
  }

  val BIT_CNT = UInt((frequency + baudRate / 2) / baudRate - 1)

  val shiftReg = Reg(init = Bits(0x3f))
  val cntReg = Reg(init = UInt(0, 20))
  val bitsReg = Reg(init = UInt(0, 4))

  io.channel.ready := (cntReg === UInt(0)) && (bitsReg === UInt(0))
  io.txd := shiftReg(0)

  // TODO: make the counter a tick generator
  when(cntReg === UInt(0)) {

    cntReg := UInt(BIT_CNT)
    when(bitsReg =/= UInt(0)) {
      val shift = shiftReg >> 1
      shiftReg := Cat(Bits(1), shift(9, 0))
      bitsReg := bitsReg - UInt(1)
    }.otherwise {
      when(io.channel.valid) {
        shiftReg(0) := Bits(0) // start bit
        shiftReg(8, 1) := io.channel.data // data
        shiftReg(10, 9) := Bits(3) // two stop bits
        bitsReg := UInt(11)
      }
      .otherwise {
      shiftReg := Bits(0x3f)
    }
  }

    }.otherwise {
      cntReg := cntReg - UInt(1)
  }

}

/**
 * A single byte buffer with an AXI style channel
  */
class Buffer extends Module {
  val io = new Bundle {
    val in = new Channel()
    val out = new Channel().flip
  }

  val empty :: full :: Nil = Enum(UInt(), 2)
  val stateReg = Reg(init = empty)
  val dataReg = Reg(init = Bits(0, 8))

  io.in.ready := stateReg === empty
  io.out.valid := stateReg === full

  when(stateReg === empty) {
      when(io.in.valid) {
        dataReg := io.in.data
        stateReg := full
      }
    }.otherwise { // full, io.out.valid := true
      when(io.out.ready) {
      stateReg := empty
    }
  }
  io.out.data := dataReg
}

/**
 * A transmitter with a single buffer.
  */
class BufferedTx(frequency: Int, baudRate: Int) extends Module {
  val io = new Bundle {
    val txd = Bits(OUTPUT, 1)
    val channel = new Channel()
  }
  val tx = Module(new Tx(frequency, baudRate))
  val buf = Module(new Buffer())

  buf.io.in <> io.channel
  tx.io.channel <> buf.io.out
  io.txd <> tx.io.txd
}

/**
 * Send 'hello'.
  */
class Sender(frequency: Int, baudRate: Int) extends Module {
  val io = new Bundle {
    val txd = Bits(OUTPUT, 1)
    val data = UInt(INPUT, 8)
    val start = Bool(INPUT)
    val done = Bool(OUTPUT)
  }

  val tx = Module(new BufferedTx(frequency, baudRate))

  io.txd := tx.io.txd

  val s_idle :: s_tx :: s_done :: Nil = Enum(UInt(), 3)
  val state = Reg(init=UInt(s_idle))

  tx.io.channel.data := io.data
  tx.io.channel.valid := Bool(false)
  io.done := Bool(false)


  switch (state) {
    is (s_idle) {
      when (io.start) { state := s_tx }
    }

    is (s_tx) {
      tx.io.channel.valid := Bool(true)
      when (tx.io.channel.ready) {
        state := s_done
      }
    }

    is (s_done) {
      io.done := Bool(true)
      when (!io.start) { state := s_idle }
    }
  }
}
