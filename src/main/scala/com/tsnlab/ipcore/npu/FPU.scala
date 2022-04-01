package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{switch, is, MuxLookup}
import fudian.{FADD,FDIV,FMUL}
import chisel3.util.Cat

import com.tsnlab.ipcore.npu.util.FPUOperand
import com.tsnlab.ipcore.util.BitUtil

object FPUState extends ChiselEnum {
  val READY = Value
  val PROCESS = Value
  val DONE = Value
}
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
  val o_valid_reg = RegInit(0.B)

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
  control.i_ready := MuxLookup(control.op, i_ready_reg, Array(
    FPUOperand.DIV -> fdiv.io.specialIO.in_ready
  ))
  control.o_valid := MuxLookup(control.op, o_valid_reg, Array(
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

  // Support for decoupled clock signal
  val state = RegInit(FPUState.READY)
  val clkcnt = RegInit(0.U(BitUtil.getBitWidth(16).W))
  val current_op = RegInit(FPUOperand.ADD)

  val clkdelay: Int = 2;

  switch (state) {
    is (FPUState.READY) {
      clkcnt := 0.U
      i_ready_reg := 1.B
      when (control.i_valid && control.i_ready) {
        when (control.op =/= FPUOperand.DIV) {
          o_valid_reg := 0.B
        }
        current_op := control.op
        state := FPUState.PROCESS
      } 
    }

    is (FPUState.PROCESS) {
      when (current_op =/= FPUOperand.DIV) {
        when (clkcnt >= (clkdelay - 1).U) {
          o_valid_reg := 1.B
          i_ready_reg := 1.B
        } otherwise {
          clkcnt := clkcnt + 1.U
          i_ready_reg := 0.B
        }
      }

      when (control.o_valid && control.o_ready) {
        clkcnt := 0.U
        state := FPUState.READY
      }
    }
  }
}
