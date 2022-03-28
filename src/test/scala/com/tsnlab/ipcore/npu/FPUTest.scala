package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import com.tsnlab.ipcore.npu.FPU
import com.tsnlab.ipcore.npu.util.FPUOperand

class FPUTest extends AnyFreeSpec with ChiselScalatestTester {
  // Standard IEEE-753 float
  val exponent = 8
  val mantissa = 23

  "FPU Sanity test: addition 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        fpu.control.op.poke(FPUOperand.ADD)
        fpu.control.i_ready.expect(1.B)
        fpu.control.o_valid.expect(1.B)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.data.y.expect("h4380_0000".U)
      }
    }
  }

  "FPU Sanity test: subtraction 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        fpu.control.op.poke(FPUOperand.SUB)
        fpu.control.i_ready.expect(1.B)
        fpu.control.o_valid.expect(1.B)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.data.y.expect("h0000_0000".U)
      }
    }
  }

  "FPU sanity test: multiply 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        fpu.control.op.poke(FPUOperand.MUL)
        fpu.control.i_ready.expect(1.B)
        fpu.control.o_valid.expect(1.B)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.data.y.expect("h4680_0000".U)
      }
    }
  }
}
