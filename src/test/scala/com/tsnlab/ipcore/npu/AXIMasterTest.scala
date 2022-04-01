package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import com.tsnlab.ipcore.axi4._
import com.tsnlab.ipcore.npu.util.BusState._

// Most tests are AXI4 compatible, does not fully test AXI3 yet
class AXIMasterTest extends AnyFreeSpec with ChiselScalatestTester {
  val axiparam = AXI4Param (
    idWidth = 12,
    addrWidth = 32,
    dataWidth = 32,
    awuserWidth = 2,
    aruserWidth = 2,
    wuserWidth = 0,
    ruserWidth = 0,
    buserWidth = 0,
    busvariant = AXIVariant.AXI3, // ZYNQ-7000 Uses AXI3
  )

  "AXI Master - Simulate and test bus read" in {
    test(new M_AXI(axiparam)).withAnnotations(Seq(WriteVcdAnnotation)) {
      aximaster => {
        var busslaveState: BusState = ARREADY

        // Some default values
        aximaster.memport_r.addr.poke("hCA7F_00D0".U)
        aximaster.memport_w.data.poke("hDEAD_F00D".U)

        // Kick it!
        aximaster.memport_r.enable.poke(1.B)

        var id: BigInt = 0

        for (i <- 1 to 512) {
          aximaster.clock.step()

          busslaveState match {
            case ARREADY => {
              aximaster.M_AXI.arready.poke(1.B)

              if (aximaster.M_AXI.arvalid.peek().litValue == 1) {
                // If it is not a catfood, something gone wrong
                aximaster.M_AXI.araddr.expect("hCA7F_00D0".U)
                id = aximaster.M_AXI.arid.peek().litValue
                busslaveState = RREADY
              }
            }

            case RREADY => {
              aximaster.M_AXI.arready.poke(0.B)
              aximaster.M_AXI.rvalid.poke(1.B)
              aximaster.M_AXI.rdata.poke("hDEAD_F00D".U)
              aximaster.M_AXI.rid.poke(id.U)
              aximaster.M_AXI.rlast.poke(1.B)

              if (aximaster.M_AXI.rready.peek().litValue == 1) {
                // Prepare for RDATA
                busslaveState = NOOP
              }
            }

            case NOOP => {
              aximaster.M_AXI.rvalid.poke(0.B)
              aximaster.memport_r.enable.poke(0.B)
            }
          }
        }
      }
    }
  }

  "AXI Master - Simulate and test bus write" in {
    test(new M_AXI(axiparam)).withAnnotations(Seq(WriteVcdAnnotation)) {
      aximaster => {
        var busslaveState: BusState = AWREADY
        // Some default values
        aximaster.memport_w.data.poke("hDEAD_F00D".U)
        aximaster.memport_w.addr.poke("hCA7F_00D0".U)

        // Kick it!
        aximaster.memport_w.enable.poke(1.B)

        var id: BigInt = 0

        for (i <- 1 to 512) {
          aximaster.clock.step()
          busslaveState match {
            case AWREADY => {
              aximaster.M_AXI.awready.poke(1.B)

              if (aximaster.M_AXI.awvalid.peek().litValue == 1) {
                aximaster.M_AXI.awaddr.expect("hCA7F_00D0".U)
                id = aximaster.M_AXI.awid.peek().litValue
                busslaveState = WREADY
              }
            }

            case WREADY => {
              aximaster.M_AXI.awready.poke(0.B)
              aximaster.M_AXI.wready.poke(1.B)

              if (aximaster.M_AXI.wvalid.peek().litValue == 1) {
                aximaster.M_AXI.wdata.expect("hDEAD_F00D".U)
                busslaveState = BREADY
              }
            }

            case BREADY => {
              aximaster.M_AXI.wready.poke(0.B)

              if (aximaster.M_AXI.bready.peek().litValue == 1) {
                aximaster.M_AXI.bid.poke(id.U)
                aximaster.M_AXI.bresp.poke(0.U)
                aximaster.M_AXI.bvalid.poke(1.B)
                busslaveState = NOOP
              }
            }

            case NOOP => {
              aximaster.M_AXI.bvalid.poke(0.U)
            }
          }
        }
      }
    }
  }
}
