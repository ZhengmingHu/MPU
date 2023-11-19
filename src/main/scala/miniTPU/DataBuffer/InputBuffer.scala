package miniTPU.DataBuffer

import chisel3._
import chisel3.util._


class InputBuffer(val IN_WIDTH: Int, val QUEUE_NUM: Int, val QUEUE_LEN: Int) extends Module {
  val io = IO(new Bundle {
    val ctrl_data_in = Input(Bool())
    val ctrl_data_out = Input(Bool())
    val ctrl_data_valid = Input(Bool())
    val data_in = Input(Vec(QUEUE_NUM, SInt(IN_WIDTH.W)))

    val data_out = Output(Vec(QUEUE_NUM, SInt(IN_WIDTH.W)))
    val all_full     = Output(Bool())
    val all_empty    = Output(Bool())
    val data_in_done = Output(Bool())
    val cal_start    = Output(Bool())  // to controller: start calculating
    val data_out_done = Output(Bool())
  })

  val data_queue = Seq.fill(QUEUE_NUM)(Module(new SyncFIFO(IN_WIDTH, QUEUE_LEN)))

  // when delay_count count to 0, queue start to output data
  // deq_count count deq element number
  // only delay_count count to 0, deq_count start to decrease
  val delay_count = Reg(Vec(QUEUE_NUM, UInt(log2Ceil(QUEUE_NUM).W)))
  val deq_count = Reg(Vec(QUEUE_NUM, UInt(log2Ceil(QUEUE_LEN + 1).W)))

  val idle :: data_in :: data_out :: Nil = Enum(3)
  val state = RegInit(idle)

  val cal_start_r = RegInit(false.B)
  cal_start_r := io.ctrl_data_out
  io.cal_start := cal_start_r

  val data_in_done = WireInit(false.B)
  val data_out_done = WireInit(false.B)
  val allFull       = WireInit(false.B)
  val allEmpty      = WireInit(false.B)

  io.data_in_done := data_in_done
  io.data_out_done := data_out_done


  for (i <- 0 until QUEUE_NUM) {
    data_queue(i).io.enq := ((state === idle && io.ctrl_data_in) || state === data_in) & io.ctrl_data_valid
    data_queue(i).io.deq := state === data_out && delay_count(i) === 0.U && deq_count(i) =/= 0.U
    data_queue(i).io.enqData := io.data_in(i)
    io.data_out(i) := data_queue(i).io.deqData
  }

  allFull := data_queue.map(_.io.full).reduce(_ & _)
  allEmpty := data_queue.map(_.io.empty).reduce(_ & _)
  io.all_full := allFull
  io.all_empty := allEmpty

  // FSM
  when(state === idle) {

    when(io.ctrl_data_in) {
      state := data_in
    }.elsewhen(io.ctrl_data_out) {
      state := data_out
    }

    for (i <- 0 until QUEUE_NUM) {
      delay_count(i) := i.U
      deq_count(i) := QUEUE_LEN.U
    }

  }.elsewhen(state === data_in) {

    when (allFull) {
      data_in_done := true.B
      state := Mux(io.ctrl_data_out, data_out, idle)
    }

  }.otherwise { // state === data_out

    when(allEmpty) {
      data_out_done := true.B
      state := idle
    }

    for (i <- 0 until QUEUE_NUM) {
      when(delay_count(i) =/= 0.U) {
        delay_count(i) := delay_count(i) - 1.U
      }.elsewhen(deq_count(i) =/= 0.U) {
        deq_count(i) := deq_count(i) - 1.U
      }
    }

  }

}









