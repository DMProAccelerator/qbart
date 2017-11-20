package rosetta

import Chisel._
import fpgatidbits.PlatformWrapper._
import fpgatidbits.dma._
import fpgatidbits.ocm._

class QBART() extends RosettaAccelerator {
  val p = PYNQParams
  val word_size = 64
  val bytes_per_elem = UInt(word_size/8, width=8)
  val numMemPorts = 3

  val io = new RosettaAcceleratorIF(numMemPorts) {

    /////// GENERAL IO
    val start = Bool(INPUT)
    val done = Bool(OUTPUT)
    val fc = Bool(INPUT)
    val conv = Bool(INPUT)
    val thresh = Bool(INPUT)
    val uart = Bool(INPUT)

    /////// UART IO
    val uart_data = UInt(INPUT, 8)

    /////// FULLY CONNECTED IO
    val lhs_addr = UInt(INPUT, width = 64)
    val rhs_addr = UInt(INPUT, width = 64)
    val res_addr = UInt(INPUT, width = 64)

    val lhs_rows = UInt(INPUT, width = 16)
    val lhs_cols = UInt(INPUT, width = 16)
    val lhs_bits = UInt(INPUT, width = 8)
    val lhs_issigned = Bool(INPUT)

    val rhs_rows = UInt(INPUT, width = 16)
    val rhs_cols = UInt(INPUT, width = 16)
    val rhs_bits = UInt(INPUT, width = 8)
    val rhs_issigned = Bool(INPUT)
    val num_chn = UInt(INPUT, width = 16)

    /////// CONVOLUTION IO

    val imageAddr = UInt(INPUT, width=64)
    val filterAddr = UInt(INPUT, width=64)
    val outputAddr = UInt(INPUT, width=64)
    val tempAddr = UInt(INPUT, width=64)

    val imageWidth = UInt(INPUT, width=32)
    val imageHeight = UInt(INPUT, width=32)
    val imageNumBits = UInt(INPUT, width=4)
    val imageNumChannels = UInt(INPUT, width=16)

    val strideExponent = UInt(INPUT, width=3)
    val windowSize = UInt(INPUT, width=4)
    val numOutputChannels = UInt(INPUT, width=16)

    val filtersNumBits = UInt(INPUT, width=4)

    val finishedSlidingWindow = Bool(OUTPUT)
    val sliderWaiting = Bool(OUTPUT)

    /////// THRESHOLDING IO

    val baseAddrRead    = UInt(INPUT, width = 64)
    val baseAddrWrite   = UInt(INPUT, width = 64)
    val byteCount       = UInt(INPUT, width = 32)
    val byteCountReader = UInt(INPUT, width = 32)
    val byteCountWriter = UInt(INPUT, width = 32)
    val elemCount       = UInt(INPUT, width = 32)
    val threshCount     = UInt(INPUT, width = 32)
  }


  // Default io

  io.done := Bool(false)

  // DRAM IO + defaults

  val reader0 = Module(new StreamReader(new StreamReaderParams(
    streamWidth = word_size, fifoElems = 8, mem = p.toMemReqParams(),
    maxBeats = 1, chanID = 0, disableThrottle = true
  ))).io

  val reader0queue = FPGAQueue(reader0.out, 2)

  reader0.baseAddr   := UInt(0)
  reader0.byteCount  := UInt(0)
  reader0.start      := Bool(false)
  reader0queue.ready  := Bool(false)
  reader0.req <> io.memPort(0).memRdReq
  reader0.rsp <> io.memPort(0).memRdRsp
  plugMemWritePort(0)

  val reader1 = Module(new StreamReader(new StreamReaderParams(
    streamWidth = word_size, fifoElems = 8, mem = p.toMemReqParams(),
    maxBeats = 1, chanID = 0, disableThrottle = true
  ))).io

  val reader1queue = FPGAQueue(reader1.out, 2)

  reader1.baseAddr   := UInt(0)
  reader1.byteCount  := UInt(0)
  reader1.start      := Bool(false)
  reader1queue.ready  := Bool(false)
  reader1.req <> io.memPort(1).memRdReq
  reader1.rsp <> io.memPort(1).memRdRsp
  plugMemWritePort(1)

  val writer = Module(new StreamWriter(new StreamWriterParams(
    streamWidth = p.memDataBits, mem = p.toMemReqParams(), chanID = 0
  ))).io
  writer.baseAddr := UInt(0)
  writer.byteCount := UInt(0)
  writer.start := Bool(false)
  writer.in.bits := UInt(0)
  writer.in.valid := Bool(false)
  writer.req <> io.memPort(2).memWrReq
  writer.wdat <> io.memPort(2).memWrDat
  writer.rsp <> io.memPort(2).memWrRsp
  plugMemReadPort(2)


  // Layer units

  val uart = Module(new Sender(50000000, 9600)).io
  uart.start := Bool(false)
  uart.data := io.uart_data
  io.tx := uart.txd

  val fc = Module(new BitserialGEMM(64, p)).io
  fc.start := Bool(false)
  fc.lhs_reader.out.valid := Bool(false)
  fc.lhs_reader.out.bits := UInt(0)
  fc.rhs_reader.out.valid := Bool(false)
  fc.rhs_reader.out.bits := UInt(0)

  fc.writer.finished := writer.finished
  fc.writer.in.ready := Bool(false)
  //val fc_writer_queue = FPGAQueue(fc.writer.in, 2)
  //fc_writer_queue.ready := Bool(false)

  fc.lhs_addr := io.lhs_addr
  fc.rhs_addr := io.rhs_addr
  fc.res_addr := io.res_addr
  fc.lhs_rows := io.lhs_rows
  fc.lhs_cols := io.lhs_cols
  fc.lhs_bits := io.lhs_bits
  fc.lhs_issigned := io.lhs_issigned
  fc.rhs_rows := io.rhs_rows
  fc.rhs_cols := io.rhs_cols
  fc.rhs_bits := io.rhs_bits
  fc.rhs_issigned := io.rhs_issigned
  fc.num_chn := io.num_chn


  val conv = Module(new Convolution(p, 64)).io

  conv.start := Bool(false)
  conv.imageAddr := io.imageAddr
  conv.filterAddr := io.filterAddr
  conv.outputAddr := io.outputAddr
  conv.tempAddr := io.tempAddr

  conv.imageWidth := io.imageWidth
  conv.imageHeight := io.imageHeight
  conv.imageNumBits := io.imageNumBits
  conv.imageNumChannels := io.imageNumChannels

  conv.strideExponent := io.strideExponent
  conv.windowSize := io.windowSize
  conv.numOutputChannels := io.numOutputChannels
  conv.filtersNumBits := io.filtersNumBits

  conv.reader0IF.out.valid := Bool(false)
  conv.reader0IF.out.bits :=  UInt(0)
  conv.reader0IF.finished :=  Bool(false)

  conv.reader1IF.out.valid := Bool(false)
  conv.reader1IF.out.bits :=  UInt(0)
  conv.reader1IF.finished :=  Bool(false)

  conv.writerIF.finished := Bool(false)
  conv.writerIF.in.ready := Bool(false)

  //val conv_writer_queue = FPGAQueue(conv.writerIF.in, 2)
  //conv_writer_queue.ready := Bool(false)

  conv.writerIF.active := Bool(false)

  io.finishedSlidingWindow := conv.finishedWithSlidingWindow
  io.sliderWaiting := conv.waiting_for_writer


  val thresh = Module(new DMAHandler(word_size, p)).io

  thresh.start := Bool(false)
  thresh.baseAddrRead := io.baseAddrRead
  thresh.baseAddrWrite := io.baseAddrWrite
  thresh.byteCount := io.byteCount
  thresh.byteCountReader := io.byteCountReader
  thresh.byteCountWriter := io.byteCountWriter
  thresh.elemCount := io.elemCount
  thresh.threshCount := io.threshCount

  thresh.reader.out.valid := Bool(false)
  thresh.reader.out.bits := UInt(0)
  thresh.reader.finished := Bool(false)

  thresh.writer.finished := Bool(false)
  thresh.writer.in.ready := UInt(0)
  thresh.writer.active := Bool(false)


  // This state machine rewires readers/writers to running layers

  val s_idle :: s_uart :: s_fc :: s_conv :: s_thresh :: s_done :: Nil = Enum(UInt(), 6)
  val state = Reg(init=UInt(s_idle))

  switch (state) {

    is (s_idle) {
      when (io.start) {
        when      (io.fc)     { state := s_fc }
        .elsewhen (io.conv)   { state := s_conv }
        .elsewhen (io.thresh) { state := s_thresh }
        .elsewhen (io.uart) { state := s_uart }
      }
    }

    is (s_uart) {
      when (uart.done) { state := s_done }
      .otherwise { uart.start := Bool(true) }  
    }
    
    is (s_fc) {
      when (fc.done) {
        state := s_done
      }
      .otherwise {
        fc.lhs_reader.out.valid := reader0queue.valid
        fc.lhs_reader.out.bits := reader0queue.bits
        fc.rhs_reader.out.valid := reader1queue.valid
        fc.rhs_reader.out.bits := reader1queue.bits
        //fc_writer_queue.ready := writer.in.ready
        fc.writer.in.ready := writer.in.ready

        reader0.baseAddr := fc.lhs_reader.baseAddr
        reader0.byteCount := fc.lhs_reader.byteCount
        reader0.start := fc.lhs_reader.start
        reader0queue.ready := fc.lhs_reader.out.ready

        reader1.baseAddr := fc.rhs_reader.baseAddr
        reader1.byteCount := fc.rhs_reader.byteCount
        reader1.start := fc.rhs_reader.start
        reader1queue.ready := fc.rhs_reader.out.ready

        writer.baseAddr := fc.writer.baseAddr
        writer.byteCount := fc.writer.byteCount
        writer.start := fc.writer.start
        //writer.in.bits := fc_writer_queue.bits
        //writer.in.valid := fc_writer_queue.valid
        writer.in.bits := fc.writer.in.bits
        writer.in.valid := fc.writer.in.valid

        fc.start := Bool(true)
      }
    }

    is (s_conv) {
      when (conv.finished) {
        state := s_done
      }
      .otherwise {
        reader0.baseAddr := conv.reader0IF.baseAddr
        reader0.byteCount := conv.reader0IF.byteCount
        reader0.start := conv.reader0IF.start
        reader0queue.ready := conv.reader0IF.out.ready

        reader1.baseAddr := conv.reader1IF.baseAddr
        reader1.byteCount := conv.reader1IF.byteCount
        reader1.start := conv.reader1IF.start
        reader1queue.ready := conv.reader1IF.out.ready

        writer.baseAddr := conv.writerIF.baseAddr
        writer.byteCount := conv.writerIF.byteCount
        writer.start := conv.writerIF.start
        writer.in.bits := conv.writerIF.in.bits
        writer.in.valid := conv.writerIF.in.valid
        //writer.in.bits := conv_writer_queue.bits
        //writer.in.valid := conv_writer_queue.valid

        conv.reader0IF.out.valid := reader0queue.valid
        conv.reader0IF.out.bits := reader0queue.bits
        conv.reader0IF.finished := reader0.finished

        conv.reader1IF.out.valid := reader1queue.valid
        conv.reader1IF.out.bits := reader1queue.bits
        conv.reader1IF.finished := reader1.finished

        conv.writerIF.finished := writer.finished
        //conv_writer_queue.ready := writer.in.ready
        conv.writerIF.in.ready := writer.in.ready
        conv.writerIF.active := writer.active

        conv.start := Bool(true)
      }
    }

    is (s_thresh) {
      when (thresh.finished) {
        state := s_done
      }
      .otherwise {
        reader0.baseAddr := thresh.reader.baseAddr
        reader0.byteCount := thresh.reader.byteCount
        reader0.start := thresh.reader.start
        //reader0.out.ready := thresh.reader.out.ready
        reader0queue.ready := thresh.reader.out.ready

        writer.baseAddr := thresh.writer.baseAddr
        writer.byteCount := thresh.writer.byteCount
        writer.start := thresh.writer.start
        writer.in.bits := thresh.writer.in.bits
        writer.in.valid := thresh.writer.in.valid

        //thresh.reader.out.valid := reader0.out.valid
        //thresh.reader.out.bits := reader0.out.bits
        thresh.reader.out.valid := reader0queue.valid
        thresh.reader.out.bits := reader0queue.bits


        thresh.writer.finished := writer.finished
        thresh.writer.in.ready := writer.in.ready
        thresh.writer.active := writer.active

	      thresh.start := Bool(true)
      }
    }

    is (s_done) {
      io.done := Bool(true)
      when (!io.start) { state := s_idle }
    }
  }
}
