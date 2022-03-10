package com.tsnlab.ipcore.npu.axi4

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.npu.axi4.io.AXI4SlaveBundle

class S_AXI_GP(axi4param: AXI4Param) extends Module {
  val S_AXI = IO(new AXI4SlaveBundle(axi4param))
  val GPIO = IO(new Bundle {
    val output = Output(UInt(8.W))
    val input = Input(UInt(8.W))
  })

  val reset_n = (!reset.asBool).asAsyncReset

  //withClock(S_AXI.clk) {
  //withClock(clock) {
  withReset(reset_n) {
    val axiReadState = RegInit(AXI4ReadState.ARVALID)
    val axiWriteState = RegInit(AXI4WriteState.AWVALID)

    // Define some registers for the AXI output
    val axi_awready = RegInit(0.B)
    val axi_arready = RegInit(0.B)
    val axi_wready  = RegInit(0.B)
    val axi_rvalid  = RegInit(0.B)
    val axi_bvalid  = RegInit(0.B)
    val axi_rdata   = RegInit(0.U(axi4param.dataWidth.W))
    val axi_rid     = RegInit(0.U(axi4param.idWidth.W))
    val axi_bid     = RegInit(0.U(axi4param.idWidth.W))

    // Wire up them to the output!
    S_AXI.awready := axi_awready
    S_AXI.arready := axi_arready
    S_AXI.wready  := axi_wready
    S_AXI.bvalid  := axi_bvalid
    S_AXI.rdata   := axi_rdata
    S_AXI.rvalid  := axi_rvalid
    S_AXI.rid     := axi_rid
    S_AXI.bid     := axi_bid

    // AXI address registers
    val axi_raddr  = RegInit(0.U(axi4param.addrWidth.W))
    val axi_waddr  = RegInit(0.U(axi4param.addrWidth.W))
    val axi_rlen   = RegInit(0.U(axi4param.burstWidth.W))
    val axi_wlen   = RegInit(0.U(axi4param.burstWidth.W))
    val axi_arid    = RegInit(0.U(axi4param.idWidth.W))
    val axi_awid    = RegInit(0.U(axi4param.idWidth.W))

    // Set up GPIO
    val gpio_reg = RegInit(0.U(8.W))
    GPIO.output := gpio_reg

    switch (axiReadState) {
      is (AXI4ReadState.ARVALID) {
        // Address Read valid
        // Wait for ARVLID signal form master and
        // Set ARREADY

        when (S_AXI.arvalid) {
          // Extract required information from the bus
          axi_rlen := S_AXI.arlen
          axi_arid := S_AXI.arid

          // Set ARREADY to 1
          axi_arready := 1.B
          axiReadState := AXI4ReadState.ARREADY
        }
      }

      is (AXI4ReadState.ARREADY) {
        // Turn off ARREADY
        axi_arready := 0.B

        axiReadState := AXI4ReadState.RVALID
      }

      is (AXI4ReadState.RVALID) {
        // Set up return data
        axi_rid := axi_arid
        // TODO: set up rresp
        axi_rdata := (0xDEADBEEFL).U // Dummy data

        // Flag RVALID
        axi_rvalid := 1.B
        
        when (S_AXI.rready) {
          axiReadState := AXI4ReadState.RREADY
        }
      }

      is (AXI4ReadState.RREADY) {
        // Clear RVALID
        axi_rvalid := 0.B

        when (axi_rlen === 0.U) {
          axiReadState := AXI4ReadState.ARVALID
        }.otherwise {
          axi_rlen := axi_rlen - 1.U
          axiReadState := AXI4ReadState.RVALID
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
        when (S_AXI.awvalid) {
          // Extract required information from the bus
          axi_waddr  := S_AXI.awaddr
          axi_wlen   := S_AXI.awlen
          axi_awid   := S_AXI.awid

          // Set AWREADY to 1
          axi_awready := 1.B
          axiWriteState := AXI4WriteState.AWREADY
        }
      }

      is (AXI4WriteState.AWREADY) {
        // Turn off AWREADY
        axi_awready := 0.B

        axiWriteState := AXI4WriteState.WVALID
      }

      is (AXI4WriteState.WVALID) {
        // Wait for WVALID
        when (S_AXI.wvalid) {
          // Write is valid.
          // Turn on WREADY
          axi_wready := 1.B
          // Proceed to next state
          axiWriteState := AXI4WriteState.WREADY
        }
      }

      is (AXI4WriteState.WREADY) {
        // Fetch data from the bus
        // ... and write it to gpio_reg
        gpio_reg := S_AXI.wdata(7,0)

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
        axi_bvalid := 1.B

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

    // Always ACK burst write
    S_AXI.bresp := 0.U
  }
}
