package com.tsnlab.ipcore.axi4

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.axi4.io.AXI4SlaveBundle

class S_AXI(axi4param: AXI4Param) extends Module {
  val S_AXI = IO(new AXI4SlaveBundle(axi4param))
  val REGMEM = IO(new Bundle {
    val we   = Output(Bool())
    val addr = Output(UInt(axi4param.addrWidth.W))
    val data = Output(UInt(axi4param.dataWidth.W))
  })

  val REGMEM_R = IO(new Bundle {
    val addr = Output(UInt(axi4param.addrWidth.W))
    val data = Input(UInt(axi4param.dataWidth.W))
  })

  val axiReadState = RegInit(AXI4ReadState.ARVALID)
  val axiWriteState = RegInit(AXI4WriteState.AWVALID)

  // Define some registers for the AXI output
  val axi_awready = RegInit(0.B)
  val axi_arready = RegInit(0.B)
  val axi_wready  = RegInit(0.B)
  val axi_rvalid  = RegInit(0.B)
  val axi_rlast   = RegInit(0.B)
  val axi_bvalid  = RegInit(0.B)
  val axi_rdata   = RegInit(0.U(axi4param.dataWidth.W))
  val axi_rid     = RegInit(0.U(axi4param.idWidth.W))
  val axi_bid     = RegInit(0.U(axi4param.idWidth.W))
  val axi_bresp   = RegInit(0.U(2.W))

  // Wire up them to the output!
  S_AXI.awready := axi_awready
  S_AXI.arready := axi_arready
  S_AXI.wready  := axi_wready
  S_AXI.bvalid  := axi_bvalid
  S_AXI.rdata   := axi_rdata
  S_AXI.rvalid  := axi_rvalid
  S_AXI.rlast   := axi_rlast
  S_AXI.rid     := axi_rid
  S_AXI.bid     := axi_bid
  S_AXI.bresp   := axi_bresp

  S_AXI.rresp   := "b00".U // OKAY

  // AXI address registers
  val axi_raddr  = RegInit(0.U(axi4param.addrWidth.W))
  val axi_waddr  = RegInit(0.U(axi4param.addrWidth.W))
  val axi_rlen   = RegInit(0.U(axi4param.getBurstWidth()))
  val axi_wlen   = RegInit(0.U(axi4param.getBurstWidth()))
  val axi_arid   = RegInit(0.U(axi4param.idWidth.W))
  val axi_awid   = RegInit(0.U(axi4param.idWidth.W))

  // Regmme

  val regmem_we  = RegInit(0.B)
  val regmem_addr= RegInit(0.U(axi4param.addrWidth.W))
  val regmem_data= RegInit(0.U(axi4param.dataWidth.W))
  val regmem_r_addr= RegInit(0.U(axi4param.addrWidth.W))
  
  REGMEM.we     := regmem_we
  REGMEM.addr   := regmem_addr
  REGMEM.data   := regmem_data
  REGMEM_R.addr := regmem_r_addr

  // TODO: Control this signals using current internal status
  axi_arready := 1.B
  axi_awready := 1.B
  //axi_wready  := 1.B

  switch (axiReadState) {
    is (AXI4ReadState.ARVALID) {
      // Address Read valid
      // Wait for ARVLID signal form master and
      // Set ARREADY
      axi_rvalid := 0.B

      when (S_AXI.arvalid && S_AXI.arready) {
        // Extract required information from the bus
        axi_rlen := S_AXI.arlen
        axi_arid := S_AXI.arid
        regmem_r_addr := S_AXI.araddr

        axiReadState := AXI4ReadState.ARREADY
        //axiReadState := AXI4ReadState.RVALID
      }
    }

    is (AXI4ReadState.ARREADY) {
      // Set up return data
      axi_rid := axi_arid
      // TODO: set up rresp
      axi_rdata := REGMEM_R.data

      // Flag RVALID
      axi_rvalid := 1.B
      
      when (axi_rlen === 0.U) {
        axi_rlast := 1.B
      } otherwise {
        axi_rlast := 0.B
      }

      axiReadState := AXI4ReadState.RVALID
    }

    is (AXI4ReadState.RVALID) {
      when (S_AXI.rready === 1.B) {
        axi_rvalid := 0.B
        when (axi_rlen === 0.U) {
          axiReadState := AXI4ReadState.ARVALID
        } otherwise {
          axi_rlen := axi_rlen - 1.U
          // TODO: Implement 64-bit operation switch
          regmem_r_addr := regmem_r_addr + 4.U // TODO: FIXME
          axiReadState := AXI4ReadState.RVALID
        }
      }
    }
  }

  switch (axiWriteState) {
    is (AXI4WriteState.AWVALID) {
      // Turn off BVALID
      axi_bvalid := 0.B
      // Address Write valid
      // Wait for AWVALID signal from master and
      // set AWREADY
      when (S_AXI.awvalid && S_AXI.awready) {
        // Extract required information from the bus
        axi_waddr  := S_AXI.awaddr
        axi_wlen   := S_AXI.awlen
        axi_awid   := S_AXI.awid

        // Set AWREADY to 1
        axiWriteState := AXI4WriteState.AWREADY
      }
    }

    is (AXI4WriteState.AWREADY) {
      // Disable write enable
      regmem_we   := 0.B

      axiWriteState := AXI4WriteState.WVALID
    }

    is (AXI4WriteState.WVALID) {
      // Wait for WVALID
      when (S_AXI.wvalid) {
        // Write is valid.
        // Turn on WREADY
        axi_wready := 1.B

        // Fetch data from the bus
        // ... and write it to gpio_reg
        regmem_we   := 1.B
        regmem_addr := axi_waddr
        regmem_data := S_AXI.wdata

        // Proceed to next state
        axiWriteState := AXI4WriteState.WREADY
      }
    }

    is (AXI4WriteState.WREADY) {
      // Turn off WREADY
      axi_wready := 0.B

      when (axi_wlen === 0.U) {
        // burst end!
        axiWriteState := AXI4WriteState.BVALID
      }.otherwise {
        axi_wlen := axi_wlen - 1.U
        axiWriteState := AXI4WriteState.WVALID
      }
    }

    is (AXI4WriteState.BVALID) {
      // Turn on BVALID
      axi_bid    := axi_awid
      axi_bvalid := 1.B
      // Always ACK burst write... for now.
      axi_bresp := 0.U
      // Disable write enable
      regmem_we   := 0.B

      // Wait for BREADY
      when (S_AXI.bready) {
        axiWriteState := AXI4WriteState.BREADY
      }
    }

    is (AXI4WriteState.BREADY) {
      // Turn off BVALID
      axi_bvalid := 0.B
      // Switch to start
      axiWriteState := AXI4WriteState.AWVALID
    }
  }
}
