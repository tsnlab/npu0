package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.ParallelTestExecution
import com.tsnlab.ipcore.axi4._
import com.tsnlab.ipcore.npu.util.BusState._

class AXISlaveTest extends AnyFreeSpec with ChiselScalatestTester with ParallelTestExecution {
  val axiparam = AXI4Param (
    idWidth = 12,
    addrWidth = 32,
    dataWidth = 32,
    userWidth = None,
    busvariant = AXIVariant.AXI3, // ZYNQ-7000 Uses AXI3
  )

  "Simulate and test bus read" in {
    test(new S_AXI(axiparam)).withAnnotations(Seq(WriteVcdAnnotation)) {
      axislave => {
        var busmaterState: BusState = ARVALID
        axislave.clock.setTimeout(65536)

        var arlen = 7;
        try {
          for (i <- 1 to 4096) {
            axislave.clock.step()

            busmaterState match {
              case ARVALID => {
                axislave.S_AXI.araddr.poke("hDEADBEEF".U)
                axislave.S_AXI.arid.poke(42.U)
                axislave.S_AXI.arburst.poke(1.U)
                axislave.S_AXI.arlen.poke(arlen.U)
                axislave.S_AXI.arsize.poke(2.U)
                axislave.S_AXI.arvalid.poke(1.B)
                busmaterState = ARREADY
              }

              case ARREADY => {
                // Wait for ARREADY signal
                if (axislave.S_AXI.arready.peek().litValue == 1) {
                  // Advance to the next stage
                  axislave.S_AXI.araddr.poke("h00000000".U)
                  axislave.S_AXI.arid.poke(0.U)
                  axislave.S_AXI.arburst.poke(0.U)
                  axislave.S_AXI.arlen.poke(0.U)
                  axislave.S_AXI.arsize.poke(0.U)
                  axislave.S_AXI.arvalid.poke(0.B)
                  busmaterState = RREADY
                }
              }

              case RREADY => {
                println("Stage RREADY")
                axislave.S_AXI.rready.poke(true.B)
                busmaterState = RVALID
              }

              case RVALID => {
                println("State RVALID")
                if (axislave.S_AXI.rvalid.peek().litValue == 1) {
                  axislave.S_AXI.rready.poke(false.B)
                  axislave.S_AXI.rid.expect(42.U)

                  if (axislave.S_AXI.rlast.peek().litValue == 1) {
                    busmaterState = NOOP
                  } else {
                    arlen -= 1
                    busmaterState = RREADY
                  }
                }
              }
              case NOOP => {
                assert(arlen == 0)
                println("Test completed.")
                throw AllDone
                // Evaluate everything
              }
            }
          }

          throw new Exception("Invalid bus state")
        } catch {
          case AllDone => 
        }
      }
    }
  }

  "Simulate and test bus write" in {
    // Configure the AXI
    test(new S_AXI(axiparam)).withAnnotations(Seq(WriteVcdAnnotation)) {
      axislave => {
        var busmasterState: BusState = AWVALID
        axislave.clock.setTimeout(65536)

        var awlen = 7;
        for(i <- 1 to 4096) {
          axislave.clock.step()

          busmasterState match {
            case AWVALID => {
              axislave.S_AXI.awaddr.poke("hFEE1DEAD".U)
              axislave.S_AXI.awid.poke(42.U)
              axislave.S_AXI.awburst.poke(1.U)
              axislave.S_AXI.awlen.poke(awlen.U)
              axislave.S_AXI.awsize.poke(2.U)
              axislave.S_AXI.awvalid.poke(1.B)
              busmasterState = AWREADY
            }

            case AWREADY => {
              // Wait for AWREADY signal
              if (axislave.S_AXI.awready.peek().litValue == 1) {
                axislave.S_AXI.awaddr.poke("h00000000".U)
                axislave.S_AXI.awid.poke(0.U)
                axislave.S_AXI.awburst.poke(0.U)
                axislave.S_AXI.awlen.poke(0.U)
                axislave.S_AXI.awsize.poke(0.U)
                axislave.S_AXI.awvalid.poke(0.B)
                busmasterState = WVALID
              }
            }

            case WVALID => {
              axislave.S_AXI.wdata.poke("hDEADBEEF".U)
              axislave.S_AXI.wstrb.poke(0.U)
              axislave.S_AXI.wvalid.poke(1.B)
              busmasterState = WREADY
            }

            case WREADY => {
              // Wait for WREADY signal
              if (axislave.S_AXI.wready.peek().litValue == 1) {
                // Use WLAST
                axislave.S_AXI.wdata.poke("h00000000".U)
                axislave.S_AXI.wstrb.poke(0.U)
                axislave.S_AXI.wvalid.poke(0.B)
                if (awlen == 0) {
                  busmasterState = BVALID
                } else {
                  awlen -= 1
                  busmasterState = WVALID
                }
              }
            }

            case BVALID => {
              if (axislave.S_AXI.bvalid.peek().litValue == 1) {
                axislave.S_AXI.bready.poke(1.B)
                busmasterState = BREADY
              }
            }

            case BREADY => {
              axislave.S_AXI.bready.poke(0.B)
              busmasterState = NOOP
            }

            case NOOP => {}
          }

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
