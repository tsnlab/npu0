package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.ParallelTestExecution
import com.tsnlab.ipcore.npu.FPU
import com.tsnlab.ipcore.npu.util.FPUOperand

object AllDone extends Exception { }
object TimedOut extends Exception { }

class FPUTest extends AnyFreeSpec with ChiselScalatestTester with ParallelTestExecution {
  // Standard IEEE-753 float
  val exponent = 8
  val mantissa = 23

  def fpuTestHelper(fpu: FPU, answer: UInt) = {
    try {
      var nextval = fpu.control.op.peek().litValue + 1;
      if (nextval >= 4) nextval = 0
      fpu.control.op.poke(nextval)
      fpu.control.i_valid.poke(0.B)
      for(i <- 1 to 16) { // It will end before 16 cyc.
        fpu.clock.step()
        if (fpu.control.o_valid.peek().litValue == 1) {
          fpu.data.y.expect(answer)
          throw AllDone
        }
      }
      throw TimedOut
    } catch {
      case AllDone => {
        fpu.clock.step()
        fpu.clock.step()
      }
    }
  }

  "FPU Sanity test: addition 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        for (i <- 1 to 4) {
          fpu.clock.step();
        }
        fpu.control.op.poke(FPUOperand.ADD)
        fpu.control.i_valid.poke(1.B)
        fpu.control.i_ready.expect(1.B)
        fpu.control.o_ready.poke(1.B)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.clock.step();

        fpuTestHelper(fpu, "h4380_0000".U)
      }
    }
  }

  "FPU Sanity test: subtraction 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        for (i <- 1 to 4) {
          fpu.clock.step();
        }
        fpu.control.op.poke(FPUOperand.SUB)
        fpu.control.i_valid.poke(1.B)
        fpu.control.i_ready.expect(1.B)
        fpu.control.o_ready.poke(1.B)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.clock.step();

        fpuTestHelper(fpu, "h0000_0000".U)
      }
    }
  }

  "FPU sanity test: multiply 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        for (i <- 1 to 4) {
          fpu.clock.step();
        }
        fpu.control.op.poke(FPUOperand.MUL)
        fpu.control.i_valid.poke(1.B)
        fpu.control.i_ready.expect(1.B)
        fpu.control.o_ready.poke(1.B)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.clock.step();

        fpuTestHelper(fpu, "h4680_0000".U)
      }
    }
  }

  "FPU sanity test: division 01" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        for (i <- 1 to 4) {
          fpu.clock.step();
        }
        fpu.control.op.poke(FPUOperand.DIV)
        fpu.data.a.poke("h43000000".U)
        fpu.data.b.poke("h43000000".U)
        fpu.control.i_valid.poke(1.B)
        fpu.control.o_ready.poke(1.B)
        fpu.control.i_ready.expect(1.B)
        fpu.clock.step();

        fpuTestHelper(fpu, "h3f80_0000".U)
      }
    }
  }
}
