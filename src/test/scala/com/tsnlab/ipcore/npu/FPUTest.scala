package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import com.tsnlab.ipcore.npu.FPU

class FPUTest extends AnyFreeSpec with ChiselScalatestTester {
  // Standard IEEE-753 float
  val exponent = 8
  val mantissa = 23

  "FPU Sanity test: addition 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        fpu.control.op.poke(0.U)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.data.y.expect("h4380_0000".U)
      }
    }
  }

  "FPU Sanity test: subtraction 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        fpu.control.op.poke(1.U)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.data.y.expect("h0000_0000".U)
      }
    }
  }
}
