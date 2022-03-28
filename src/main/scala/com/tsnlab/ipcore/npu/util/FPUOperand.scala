package com.tsnlab.ipcore.npu.util

import chisel3._
import chisel3.experimental.ChiselEnum

object FPUOperand extends ChiselEnum {
  val ADD = 0.U(2.W)
  val SUB = 1.U(2.W)
  val MUL = 2.U(2.W)
  val DIV = 3.U(2.W)
}
