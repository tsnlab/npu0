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

  val reg_a = RegInit(0.U(data.a.getWidth.W))
  val reg_b = RegInit(0.U(data.b.getWidth.W))
  val reg_y = RegInit(0.U(data.y.getWidth.W))
  val current_op = RegInit(FPUOperand.ADD)

  val i_ready_reg = RegInit(1.B)
  val o_valid_reg = RegInit(0.B)

  val fadd = Module(new FADD(exponent, mantissa + 1))
  val fdiv = Module(new FDIV(exponent, mantissa + 1))
  val fmul = Module(new FMUL(exponent, mantissa + 1))

  fadd.io.a  := reg_a
  fdiv.io.a  := reg_a
  fmul.io.a  := reg_a

  when (current_op === FPUOperand.SUB) {
    fadd.io.b := Cat(!reg_b(exponent + mantissa),
                     reg_b(exponent + mantissa - 1, 0))
  } otherwise {
    fadd.io.b := reg_b
  }
  fdiv.io.b  := reg_b
  fmul.io.b  := reg_b
  fadd.io.rm := 0.U
  fdiv.io.rm := 0.U
  fmul.io.rm := 0.U

  fdiv.io.specialIO.kill := 0.B
  fdiv.io.specialIO.isSqrt := 0.B

  // Support for decoupled clock signal
  val state = RegInit(FPUState.READY)
  val clkcnt = RegInit(0.U(BitUtil.getBitWidth(16).W))

  // Wire control signal to fdiv value
  fdiv.io.specialIO.in_valid := MuxLookup(current_op, 0.B, Array(
    FPUOperand.DIV -> 1.B //control.i_valid
  ))
  fdiv.io.specialIO.out_ready := MuxLookup(current_op, 0.B, Array(
    FPUOperand.DIV -> control.o_ready
  ))

  // TODO: 여기 valid/ready 찔러봐야 함, Simulation task
  control.i_ready := MuxLookup(current_op, i_ready_reg, Array(
    FPUOperand.DIV -> fdiv.io.specialIO.in_ready
  ))
  control.o_valid := MuxLookup(current_op, o_valid_reg, Array(
    FPUOperand.DIV -> fdiv.io.specialIO.out_valid
  ))

  //data.y := reg_y
  data.y := MuxLookup(current_op, reg_y, Array(
    FPUOperand.DIV -> fdiv.io.result
  ))
  
  val clkdelay: Int = 1;

  switch (state) {
    is (FPUState.READY) {
      clkcnt := 0.U
      i_ready_reg := 1.B
      when (control.i_valid && control.i_ready) {
        when (control.op =/= FPUOperand.DIV) {
          o_valid_reg := 0.B
        }
        current_op := control.op
        reg_a := data.a
        reg_b := data.b
        i_ready_reg := 0.B
        state := FPUState.PROCESS
      } 
    }

    is (FPUState.PROCESS) {
      when (current_op =/= FPUOperand.DIV) {
        when (clkcnt >= clkdelay.U) {
          o_valid_reg := 1.B
          i_ready_reg := 1.B
          reg_y := MuxLookup(current_op, DontCare, Array(
            FPUOperand.ADD -> fadd.io.result,
            FPUOperand.SUB -> fadd.io.result,
            FPUOperand.MUL -> fmul.io.result,
          ))

        } otherwise {
          clkcnt := clkcnt + 1.U
          i_ready_reg := 0.B
        }
      }

      when (control.o_valid && control.o_ready) {
        state := FPUState.READY
      }
    }
  }
}
