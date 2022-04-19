package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{switch, is, MuxLookup, Decoupled}
import fudian.{FADD,FDIV,FMUL}
import chisel3.util.Cat

import com.tsnlab.ipcore.npu.util.FPUOperand
import com.tsnlab.ipcore.util.BitUtil

object AsyncFPUState extends ChiselEnum {
  val READY = Value
  val PROCESS = Value
  val DONE = Value
}

class AsyncFPU(exponent: Int, mantissa: Int) extends Module {
  val data = IO(new Bundle{
    val a = Flipped(Decoupled(UInt((exponent + mantissa + 1).W)))
    val b = Flipped(Decoupled(UInt((exponent + mantissa + 1).W)))
    val y = Decoupled(UInt((exponent + mantissa + 1).W))
  })

  val control = IO(new Bundle{
    val op = Input(UInt(2.W))
  })

  val current_op = RegInit(FPUOperand.ADD)

  val fadd = Module(new FADD(exponent, mantissa + 1))
  val fdiv = Module(new FDIV(exponent, mantissa + 1))
  val fmul = Module(new FMUL(exponent, mantissa + 1))

  fadd.io.a  := data.a.bits
  fdiv.io.a  := data.a.bits
  fmul.io.a  := data.a.bits

  when (current_op === FPUOperand.SUB) {
    fadd.io.b := Cat(!data.b.bits(exponent + mantissa),
                     data.b.bits(exponent + mantissa - 1, 0))
  } otherwise {
    fadd.io.b := data.b.bits
  }
  fdiv.io.b  := data.b.bits
  fmul.io.b  := data.b.bits

  fadd.io.rm := 0.U
  fdiv.io.rm := 0.U
  fmul.io.rm := 0.U

  fdiv.io.specialIO.kill := 0.B
  fdiv.io.specialIO.isSqrt := 0.B

  data.a.ready := 0.B
  data.b.ready := 0.B
  data.y.valid := 0.B

  // Support for decoupled clock signal
  val state = RegInit(FPUState.READY)
  val clkcnt = RegInit(0.U(BitUtil.getBitWidth(16).W))

  // Wire control signal to fdiv value
  fdiv.io.specialIO.in_valid := 0.B
  //MuxLookup(current_op, 0.B, Array(
  //  FPUOperand.DIV -> (data.a.valid & data.b.valid)
  //))
  fdiv.io.specialIO.out_ready := MuxLookup(current_op, 0.B, Array(
    FPUOperand.DIV -> data.y.ready
  ))

  //data.y := reg_y
  data.y.bits := MuxLookup(current_op, DontCare, Array(
    FPUOperand.ADD -> fadd.io.result,
    FPUOperand.SUB -> fadd.io.result,
    FPUOperand.MUL -> fmul.io.result,
    FPUOperand.DIV -> fdiv.io.result
  ))
  
  val clkdelay: Int = 1;

  switch (state) {
    is (FPUState.READY) {
      clkcnt := 0.U
      current_op := 0.B
      when (data.a.valid && data.b.valid) {
        current_op := control.op
        when (control.op === FPUOperand.DIV) {
          fdiv.io.specialIO.in_valid := 1.B
        }
        state := FPUState.PROCESS
      }
    }

    is (FPUState.PROCESS) {
      when (current_op =/= FPUOperand.DIV) {
        when (clkcnt >= clkdelay.U) {
          when (data.y.ready) {
            data.a.ready := 1.B
            data.b.ready := 1.B
            data.y.valid := 1.B
            state := FPUState.READY
          }
        } otherwise {
          clkcnt := clkcnt + 1.U
        }
      } otherwise {
        when (fdiv.io.specialIO.out_valid) {
          data.a.ready := 1.B
          data.b.ready := 1.B
          data.y.valid := 1.B
          state := FPUState.READY
        }
      }
    }
  }
}
