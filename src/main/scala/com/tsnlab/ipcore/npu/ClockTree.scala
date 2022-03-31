package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{switch, is, MuxLookup}

class ClockTree extends Module {
  val clko = IO(new Bundle {
    val div2 = Output(Bool())
    val div4 = Output(Bool())
    val div8 = Output(Bool())
  })

  val divreg = RegInit(0.U(3.W))
  divreg := divreg + 1.U

  clko.div2 := divreg(0)
  clko.div4 := divreg(1)
  clko.div8 := divreg(2)
}
