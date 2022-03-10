package com.tsnlab.ipcore.npu.axi4

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.npu.axi4.io.AXI4MasterBundle

// ARM AXI High Performance interface (master)
class M_AXI_HP(axi4param: AXI4Param) extends Module {
  val M_AXI = IO(new AXI4MasterBundle(axi4param))

  val axiReadState = RegInit(AXI4ReadState.ARVALID)
  val axiWriteState = RegInit(AXI4WriteState.AWVALID)

  // Define some registers for the AXI output
  // Bus signals
  val axi_awvalid = RegInit(0.B)
  val axi_arvalid = RegInit(0.B)
  val axi_wvalid  = RegInit(0.B)
  val axi_rready  = RegInit(0.B)

  // Bus data
  val axi_arsize  = RegInit(0.U(3.W))
  val axi_arburst = RegInit(0.U(2.W))
  val axi_arlock  = RegInit(0.U(2.W))
  val axi_arcache = RegInit(0.U(4.W))

  // Wire up them to the output
  M_AXI.awvalid := axi_awvalid
  M_AXI.arvalid := axi_arvalid
  M_AXI.wvalid := axi_wvalid
  M_AXI.rready := axi_rready

  // First, define the state machine for read
  switch (axiReadState) {
    is (AXI4ReadState.ARVALID) {
      // TODO: Implement Read Address configuration
      // Sets up ARVALID
      axi_arvalid := 1.B
      // Transit to next state
      axiReadState := AXI4ReadState.ARREADY
    }

    is (AXI4ReadState.ARREADY) {
      when (M_AXI.arready) {
        axi_arvalid := 0.B
        axiReadState := AXI4ReadState.RVALID
      }
    }

    is (AXI4ReadState.RVALID) {
      when (M_AXI.rvalid) {
        axi_rready := 1.B
        axiReadState := AXI4ReadState.RREADY
      }
    }

    is (AXI4ReadState.RREADY) {
      axiReadState := AXI4ReadState.ARVALID
    }
  }

  switch (axiWriteState) {
    is (AXI4WriteState.AWVALID) {
      //
    }

    is (AXI4WriteState.AWREADY) {
      //
    }

    is (AXI4WriteState.WVALID) {
      //
    }

    is (AXI4WriteState.WREADY) {
      //
    }

    is (AXI4WriteState.BVALID) {
      //
    }

    is (AXI4WriteState.BREADY) {
      //
    }
  }
}
