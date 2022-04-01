package com.tsnlab.ipcore.npu


import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.ParallelTestExecution
import com.tsnlab.ipcore.npu.ClockTree

class ClockTreeTest extends AnyFreeSpec with ChiselScalatestTester with ParallelTestExecution {
  "Simulate the clock tree" in {
    test(new ClockTree).withAnnotations(Seq(WriteVcdAnnotation)) {
      clocktree => {
        for (i <- 1 to 128) {
          clocktree.clock.step()
        }
      }
    }
  }
}
