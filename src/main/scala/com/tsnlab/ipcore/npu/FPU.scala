package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.util.{switch, is, MuxLookup}
import fudian.{FADD,FDIV,FMUL}
import chisel3.util.Cat

import com.tsnlab.ipcore.npu.util.FPUOperand

class FPU(exponent: Int, mantissa: Int) extends Module {
  val data = IO(new Bundle{
    val a = Input(UInt((exponent + mantissa + 1).W))
    val b = Input(UInt((exponent + mantissa + 1).W))
    val y = Output(UInt((exponent + mantissa + 1).W))
  })
  val control = IO(new Bundle{
    val op = Input(UInt(2.W))
    val i_ready = Output(Bool())
    val i_valid = Input(Bool())
    val o_ready = Input(Bool())
    val o_valid = Output(Bool())
  })

  val i_ready_reg = RegInit(1.B)
  val o_valid_reg = RegInit(1.B)

  val fadd = Module(new FADD(exponent, mantissa + 1))
  val fdiv = Module(new FDIV(exponent, mantissa + 1))
  val fmul = Module(new FMUL(exponent, mantissa + 1))

  fadd.io.a  := data.a
  fdiv.io.a  := data.a
  fmul.io.a  := data.a

  when (control.op === 1.U) {
    fadd.io.b := Cat(!data.b(exponent + mantissa),
                     data.b(exponent + mantissa - 1, 0))
  } otherwise {
    fadd.io.b := data.b
  }
  fdiv.io.b  := data.b
  fmul.io.b  := data.b
  fadd.io.rm := 0.U
  fdiv.io.rm := 0.U
  fmul.io.rm := 0.U

  // Wire control signal to fdiv value
  fdiv.io.specialIO.in_valid := MuxLookup(control.op, 0.B, Array(
    FPUOperand.DIV -> control.i_valid
  ))
  fdiv.io.specialIO.out_ready := MuxLookup(control.op, 0.B, Array(
    FPUOperand.DIV -> control.o_ready
  ))
  control.i_ready := MuxLookup(control.op, 1.B, Array(
    FPUOperand.DIV -> fdiv.io.specialIO.in_ready
  ))
  control.o_valid := MuxLookup(control.op, 1.B, Array(
    FPUOperand.DIV -> fdiv.io.specialIO.out_valid
  ))

  fdiv.io.specialIO.kill := 0.B
  fdiv.io.specialIO.isSqrt := 0.B

  data.y := MuxLookup(control.op, "hFFFF_FFFF".U, Array(
    FPUOperand.ADD -> fadd.io.result,
    FPUOperand.SUB -> fadd.io.result,
    FPUOperand.MUL -> fmul.io.result,
    FPUOperand.DIV -> fdiv.io.result,
  ))
}
