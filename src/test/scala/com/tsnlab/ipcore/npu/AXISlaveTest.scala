package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import com.tsnlab.ipcore.npu.axi4._

class AXISlaveTest extends AnyFreeSpec with ChiselScalatestTester {
  "Simulate AXI" in {
    // Configure the AXI
    val axiparam = AXI4Param (
      idWidth = 12,
      addrWidth = 32,
      dataWidth = 32,
      burstWidth = 4, // ZYNQ-7000 Uses AXI3
      awuserWidth = 2,
      aruserWidth = 2,
      wuserWidth = 0,
      ruserWidth = 0,
      buserWidth = 0,
      isLite = false,
    )

    test(new S_AXI_GP(axiparam)).withAnnotations(Seq(WriteVcdAnnotation)) {
      axislave => {
        //axislave.S_AXI.clk.setTimeout(65536)
        axislave.clock.setTimeout(65536)
        for(i <- 1 to 4096) {
          //axislave.S_AXI.clk.step()
          axislave.clock.step()
          if (i > 10) {
            // Send the signal
            axislave.S_AXI.awvalid.poke(1.B)
          }
          //
        }
      }
    }
  }
}
