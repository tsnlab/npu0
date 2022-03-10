package com.tsnlab.ipcore.npu.axi4

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.npu.axi4.io.AXI4MasterBundle

// ARM AXI High Performance interface (master)
class M_AXI(axi4param: AXI4Param) extends Module {
  val M_AXI = IO(new AXI4MasterBundle(axi4param))

  val axiReadState = RegInit(AXI4ReadState.ARVALID)
  val axiWriteState = RegInit(AXI4WriteState.AWVALID)

  // Define some registers for the AXI output

  // AR channel
  val axi_araddr  = RegInit(0.U(axi4param.addrWidth.W))
  val axi_arlen   = RegInit(0.U(axi4param.burstWidth.W))
  val axi_arsize  = RegInit(0.U(3.W))
  val axi_arburst = RegInit(0.U(2.W))
  val axi_arlock  = RegInit(0.U(2.W))
  val axi_arcache = RegInit(0.U(4.W))
  val axi_arprot  = RegInit(0.U(3.W))
  val axi_arid    = RegInit(0.U(axi4param.idWidth.W))
  val axi_arqos   = RegInit(0.U(4.W))
  val axi_arvalid = RegInit(0.B)

  M_AXI.araddr   := axi_araddr
  M_AXI.arlen    := axi_arlen
  M_AXI.arsize   := axi_arsize
  M_AXI.arburst  := axi_arburst
  M_AXI.arlock   := axi_arlock
  M_AXI.arcache  := axi_arcache
  M_AXI.arprot   := axi_arprot
  M_AXI.arid     := axi_arid
  M_AXI.arqos    := axi_arqos
  M_AXI.arvalid  := axi_arvalid

  // AW channel
  val axi_awaddr  = RegInit(0.U(axi4param.addrWidth.W))
  val axi_awlen   = RegInit(0.U(axi4param.burstWidth.W))
  val axi_awsize  = RegInit(0.U(3.W))
  val axi_awburst = RegInit(0.U(2.W))
  val axi_awlock  = RegInit(0.U(2.W))
  val axi_awcache = RegInit(0.U(4.W))
  val axi_awprot  = RegInit(0.U(3.W))
  val axi_awid    = RegInit(0.U(axi4param.idWidth.W))
  val axi_awqos   = RegInit(0.U(4.W))
  val axi_awvalid = RegInit(0.B)

  M_AXI.awaddr   := axi_awaddr
  M_AXI.awlen    := axi_awlen
  M_AXI.awsize   := axi_awsize
  M_AXI.awburst  := axi_awburst
  M_AXI.awlock   := axi_awlock
  M_AXI.awcache  := axi_awcache
  M_AXI.awprot   := axi_awprot
  M_AXI.awid     := axi_awid
  M_AXI.awqos    := axi_awqos
  M_AXI.awvalid  := axi_awvalid

  // W channel
  val axi_wdata   = RegInit(0.U(axi4param.dataWidth.W))
  val axi_wstrb   = RegInit(0.U(((axi4param.dataWidth + 7) / 8).W)) // See A10.3.4
  val axi_wlast   = RegInit(0.B)
  val axi_wvalid  = RegInit(0.B)

  M_AXI.wdata    := axi_wdata
  M_AXI.wstrb    := axi_wstrb
  M_AXI.wlast    := axi_wlast
  M_AXI.wvalid   := axi_wvalid

  // R channel
  val axi_rlast   = RegInit(0.B)
  val axi_rready  = RegInit(0.B)

  M_AXI.rlast    := axi_rlast
  M_AXI.rready   := axi_rready

  // B channel
  val axi_bready  = RegInit(0.B)
  M_AXI.bready   := axi_bready
  // Wire up bus data

  // First, define the state machine for read
  switch (axiReadState) {
    is (AXI4ReadState.ARVALID) {
      // TODO: Implement Read Address configuration
      // Set up dummy value for master
      axi_arburst := 1.U // MODE INCR
      axi_arlen := 0.U // Only fetch one time
      axi_arid := 1.U // TODO: FIXME: Find out right ID for this
      axi_arsize := 2.U // TODO: Read the manual and find out

      // Sets up ARVALID
      axi_arvalid := 1.B
      //
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
      // TODO: Check ARID match
      when (M_AXI.rvalid) {
        axi_rready := 1.B
        axiReadState := AXI4ReadState.RREADY
      }
    }

    is (AXI4ReadState.RREADY) {
      // TODO: Implement burst counter properly
      axi_rready := 0.B
      axiReadState := AXI4ReadState.ARVALID
    }
  }

  switch (axiWriteState) {
    is (AXI4WriteState.AWVALID) {
      axi_awvalid := 1.B
      axiWriteState := AXI4WriteState.AWREADY
    }

    is (AXI4WriteState.AWREADY) {
      when (M_AXI.awready) {
        axi_awvalid := 0.B
        axiWriteState := AXI4WriteState.WVALID
      }
    }

    is (AXI4WriteState.WVALID) {
      axi_wvalid := 1.B
    }

    is (AXI4WriteState.WREADY) {
      // TODO: Implement burst counter properly
      when (M_AXI.wready) {
        axi_wvalid := 0.B
        axiWriteState := AXI4WriteState.BVALID
      }
    }

    is (AXI4WriteState.BVALID) {
      when (M_AXI.bvalid) {
        axi_bready := 1.B
        axiWriteState := AXI4WriteState.BREADY
      }
    }

    is (AXI4WriteState.BREADY) {
      axi_bready := 0.B
      axiWriteState := AXI4WriteState.AWVALID
    }
  }
}
