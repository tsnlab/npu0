package com.tsnlab.ipcore.fifo

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{switch, is, MuxLookup}
import chisel3.util.Cat
import com.tsnlab.ipcore.util.BitUtil

class FIFO(width: Int, length: Int) extends Module {
  val input = IO(new Bundle {
    val en      = Input(Bool())
    val ready   = Output(Bool())
    val data    = Input(UInt(width.W))
  })

  val output = IO(new Bundle {
    val en      = Input(Bool())
    val ready   = Output(Bool())
    val data    = Output(UInt(width.W))
  })

  if (!BitUtil.isPowerOfTwo(length)) {
    throw new Error("Length must be power of two")
  }

  val mem = SyncReadMem(length, UInt(width.W))

  val cnt_i = RegInit(0.U(BitUtil.getBitWidth(length).W))
  val cnt_o = RegInit(0.U(BitUtil.getBitWidth(length).W))

  input.ready := cnt_o - 1.U(cnt_o.getWidth.W) =/= cnt_i
  output.ready := cnt_i - cnt_o > 0.U

  output.data := DontCare

  when (input.en && input.ready) {
    val port = mem(cnt_i)
    port := input.data
    cnt_i := cnt_i + 1.U
  }

  when (output.en && output.ready) {
    val port = mem(cnt_o)
    output.data := port
    cnt_o := cnt_o + 1.U
  }
}
