package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.ParallelTestExecution
import com.tsnlab.ipcore.npu.FPU
import com.tsnlab.ipcore.npu.util.FPUOperand

import java.util.Random

class FPUMulticycleTest extends AnyFreeSpec with ChiselScalatestTester with ParallelTestExecution {
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
          //fpu.data.y.expect(answer)
          throw AllDone
        }
      }
      throw TimedOut
    } catch {
      case AllDone => {
      //  fpu.clock.step()
      //  fpu.clock.step()
      }
    }
  }

  "FPU multicycle test: addition 03" in {
    test(new FPU(exponent, mantissa)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpu => {
        val random = new Random();

        for (tcase <- 1 to 4) {
          var a = random.nextInt();
          var b = random.nextInt();

          if (a < 0) a = -a
          if (b < 0) b = -b
          for (i <- 1 to 4) {
            fpu.clock.step()
          }
          fpu.control.op.poke(FPUOperand.ADD)
          fpu.control.i_valid.poke(1.B)
          fpu.control.i_ready.expect(1.B)
          fpu.control.o_ready.poke(1.B)
          fpu.data.a.poke(a.U)
          fpu.data.b.poke(b.U)
          fpu.clock.step()

          fpuTestHelper(fpu, "h4380_0000".U)

          fpu.clock.step()
        }
      }
    }
  }

}
