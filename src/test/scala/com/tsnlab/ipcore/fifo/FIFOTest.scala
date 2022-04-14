package com.tsnlab.ipcore.fifo

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.ParallelTestExecution
import com.tsnlab.ipcore.fifo._

class FIFOTest extends AnyFreeSpec with ChiselScalatestTester with ParallelTestExecution {
  "FIFO Queue state test" in {
    test(new FIFO(32, 8)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fifo => {
        fifo.clock.step()

        fifo.input.en.poke(0.B)
        fifo.input.ready.expect(1.B)
        fifo.output.ready.expect(0.B)
      }
    }
  }

  "FIFO Queue IO test" in {
    test(new FIFO(32, 4)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fifo => {
        fifo.clock.step()

        fifo.input.ready.expect(1.B)
        fifo.output.ready.expect(0.B)
        fifo.input.en.poke(1.B)
        fifo.input.data.poke("hDEADBEEF".U)

        fifo.clock.step()

        fifo.input.ready.expect(1.B)
        fifo.output.ready.expect(1.B)
        fifo.input.data.poke("hFEE1DEAD".U)

        fifo.clock.step()

        fifo.input.ready.expect(1.B)
        fifo.output.ready.expect(1.B)
        fifo.input.data.poke("h0CA7F00D".U)

        fifo.clock.step()
        fifo.input.en.poke(0.B)
        fifo.input.ready.expect(0.B)
        fifo.output.ready.expect(1.B)

        fifo.output.en.poke(1.B)

        fifo.clock.step()

        fifo.output.data.expect("hDEADBEEF".U)

        fifo.clock.step()
        fifo.input.ready.expect(1.B)
        fifo.output.ready.expect(1.B)
        fifo.output.data.expect("hFEE1DEAD".U)

        fifo.clock.step()

        fifo.input.ready.expect(1.B)
        fifo.output.ready.expect(0.B)
        fifo.output.data.expect("h0CA7F00D".U)
      }
    }
  }
}