package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.tester.testableClock
import chiseltest.{ChiselScalatestTester, testableUInt}
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.ParallelTestExecution
import com.tsnlab.ipcore.axi4._
import com.tsnlab.ipcore.npu.util.BusState._

class FPUWrapperTest extends AnyFreeSpec with ChiselScalatestTester with ParallelTestExecution {
  val axi3MasterParam = AXI4Param (
    idWidth = 6,
    addrWidth = 32,
    dataWidth = 32,
    awuserWidth = 2,
    aruserWidth = 2,
    wuserWidth = 0,
    ruserWidth = 0,
    buserWidth = 0,
    busvariant = AXIVariant.AXI3, // ZYNQ-7000 Uses AXI3
  )

  val axi3SlaveParam = AXI4Param (
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

  "FPUWrapper - Simulate" in {
    test(new FPUWrapper(axi3MasterParam, axi3SlaveParam)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpuwrapper => {
        var busmasterState: BusState = AWVALID
        fpuwrapper.clock.setTimeout(65535)

        // Dummy bus master
        fpuwrapper.M_AXI.arready.poke(1.B)
        fpuwrapper.M_AXI.awready.poke(1.B)
        fpuwrapper.M_AXI.rvalid.poke(1.B)
        fpuwrapper.M_AXI.wready.poke(1.B)
        fpuwrapper.M_AXI.bvalid.poke(1.B)

        //
        for(i <- 1 to 512) {
          fpuwrapper.clock.step()

          // Write to AXI3 slave port
          busmasterState match {
            case AWVALID => {
              fpuwrapper.S_AXI.awaddr.poke("h4000_0000".U)
              fpuwrapper.S_AXI.awid.poke(42.U)
              fpuwrapper.S_AXI.awburst.poke(1.U)
              fpuwrapper.S_AXI.awlen.poke(0.U)
              fpuwrapper.S_AXI.awsize.poke(2.U)
              fpuwrapper.S_AXI.awvalid.poke(1.B)
              busmasterState = AWREADY
            }

            case AWREADY => {
              // Wait for AWREADY signal
              if (fpuwrapper.S_AXI.awready.peek().litValue == 1) {
                fpuwrapper.S_AXI.awaddr.poke("h0000_0000".U)
                fpuwrapper.S_AXI.awid.poke(0.U)
                fpuwrapper.S_AXI.awburst.poke(0.U)
                fpuwrapper.S_AXI.awlen.poke(0.U)
                fpuwrapper.S_AXI.awsize.poke(0.U)
                fpuwrapper.S_AXI.awvalid.poke(0.B)
                busmasterState = WVALID
              }
            }

            case WVALID => {
              fpuwrapper.S_AXI.wdata.poke("h0000_0001".U)
              fpuwrapper.S_AXI.wstrb.poke(0.U)
              fpuwrapper.S_AXI.wvalid.poke(1.B)
              busmasterState = WREADY
            }

            case WREADY => {
              // Wait for WREADY signal
              if (fpuwrapper.S_AXI.wready.peek().litValue == 1) {
                // Use WLAST
                fpuwrapper.S_AXI.wdata.poke("h0000_0000".U)
                fpuwrapper.S_AXI.wstrb.poke(0.U)
                fpuwrapper.S_AXI.wvalid.poke(0.B)
                busmasterState = BVALID
              }
            }

            case BVALID => {
              if (fpuwrapper.S_AXI.bvalid.peek().litValue == 1) {
                fpuwrapper.S_AXI.bready.poke(1.B)
                busmasterState = BREADY
              }
            }

            case BREADY => {
              fpuwrapper.S_AXI.bready.poke(0.B)
              busmasterState = NOOP
            }

            case NOOP => {}
          }
        }
      }
    }
  }

  "FPUWrapper - Simulate DIV" in {
    test(new FPUWrapper(axi3MasterParam, axi3SlaveParam)).withAnnotations(Seq(WriteVcdAnnotation)) {
      fpuwrapper => {
        var busmasterState: BusState = AWVALID
        fpuwrapper.clock.setTimeout(65535)

        // Dummy bus master
        fpuwrapper.M_AXI.arready.poke(1.B)
        fpuwrapper.M_AXI.awready.poke(1.B)
        fpuwrapper.M_AXI.rvalid.poke(1.B)
        fpuwrapper.M_AXI.rdata.poke("h4300_0000".U)
        fpuwrapper.M_AXI.wready.poke(1.B)
        fpuwrapper.M_AXI.bvalid.poke(1.B)

        //
        for(i <- 1 to 512) {
          fpuwrapper.clock.step()

          // Write to AXI3 slave port
          busmasterState match {
            case AWVALID => {
              fpuwrapper.S_AXI.awaddr.poke("h4000_0000".U)
              fpuwrapper.S_AXI.awid.poke(42.U)
              fpuwrapper.S_AXI.awburst.poke(1.U)
              fpuwrapper.S_AXI.awlen.poke(0.U)
              fpuwrapper.S_AXI.awsize.poke(2.U)
              fpuwrapper.S_AXI.awvalid.poke(1.B)
              busmasterState = AWREADY
            }

            case AWREADY => {
              // Wait for AWREADY signal
              if (fpuwrapper.S_AXI.awready.peek().litValue == 1) {
                fpuwrapper.S_AXI.awaddr.poke("h0000_0000".U)
                fpuwrapper.S_AXI.awid.poke(0.U)
                fpuwrapper.S_AXI.awburst.poke(0.U)
                fpuwrapper.S_AXI.awlen.poke(0.U)
                fpuwrapper.S_AXI.awsize.poke(0.U)
                fpuwrapper.S_AXI.awvalid.poke(0.B)
                busmasterState = WVALID
              }
            }

            case WVALID => {
              fpuwrapper.S_AXI.wdata.poke("h0000_0301".U)
              fpuwrapper.S_AXI.wstrb.poke(0.U)
              fpuwrapper.S_AXI.wvalid.poke(1.B)
              busmasterState = WREADY
            }

            case WREADY => {
              // Wait for WREADY signal
              if (fpuwrapper.S_AXI.wready.peek().litValue == 1) {
                // Use WLAST
                fpuwrapper.S_AXI.wdata.poke("h0000_0000".U)
                fpuwrapper.S_AXI.wstrb.poke(0.U)
                fpuwrapper.S_AXI.wvalid.poke(0.B)
                busmasterState = BVALID
              }
            }

            case BVALID => {
              if (fpuwrapper.S_AXI.bvalid.peek().litValue == 1) {
                fpuwrapper.S_AXI.bready.poke(1.B)
                busmasterState = BREADY
              }
            }

            case BREADY => {
              fpuwrapper.S_AXI.bready.poke(0.B)
              busmasterState = NOOP
            }

            case NOOP => {}
          }
        }
      }
    }
  }
}
