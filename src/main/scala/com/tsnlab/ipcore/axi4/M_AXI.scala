package com.tsnlab.ipcore.axi4

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.axi4.io.AXI4MasterBundle

// ARM AXI High Performance interface (master)
class M_AXI(axi4param: AXI4Param) extends Module {
  val M_AXI = IO(new AXI4MasterBundle(axi4param))

  // AW channel
  val axi_awaddr  = RegInit(0.U(axi4param.addrWidth.W))
  val axi_awlen   = RegInit(0.U(axi4param.getBurstWidth()))
  val axi_awsize  = RegInit(0.U(3.W))
  val axi_awburst = RegInit(0.U(2.W))
  val axi_awlock  = RegInit(0.U(2.W))
  val axi_awcache = RegInit(0.U(4.W))
  val axi_awprot  = RegInit(0.U(3.W))
  val axi_awid    = RegInit(0.U(axi4param.idWidth.W))
  val axi_awqos   = RegInit(0.U(4.W)) // See A8.1.1
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
  val axi_wstrb   = RegInit(0.U(axi4param.getStrbWidth()))
  val axi_wlast   = RegInit(0.B)
  val axi_wvalid  = RegInit(0.B)

  M_AXI.wdata    := axi_wdata
  M_AXI.wstrb    := axi_wstrb
  M_AXI.wlast    := axi_wlast
  M_AXI.wvalid   := axi_wvalid

  val axi_wid     = RegInit(0.U(axi4param.dataWidth.W))
  // If it has WID, wire it up.
  // Mostly, AXI3 specific.
  M_AXI.wid match {
    case wid: UInt => wid := axi_wid
    case _ => {}
  }

  // AR channel
  val axi_araddr  = RegInit(0.U(axi4param.addrWidth.W))
  val axi_arlen   = RegInit(0.U(axi4param.getBurstWidth()))
  val axi_arsize  = RegInit(0.U(3.W))
  val axi_arburst = RegInit(0.U(2.W))
  val axi_arlock  = RegInit(0.U(2.W))
  val axi_arcache = RegInit(0.U(4.W))
  val axi_arprot  = RegInit(0.U(3.W))
  val axi_arid    = RegInit(0.U(axi4param.idWidth.W))
  val axi_arqos   = RegInit(0.U(4.W)) // See A8.1.1
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

  val axi_rready  = RegInit(0.B)

  M_AXI.rready   := axi_rready

  val axi_bready  = RegInit(0.B)

  M_AXI.bready   := axi_bready

  val memport_r = IO(new Bundle {
    val addr = Input(UInt(axi4param.addrWidth.W))
    val data = Output(UInt(axi4param.dataWidth.W))
    val cache = Input(UInt(4.W))
    val enable = Input(Bool())
    val ready = Output(Bool())
  })

  val memport_r_data = RegInit(0.U(axi4param.dataWidth.W))
  val memport_r_ready = RegInit(0.B)

  memport_r.data  := memport_r_data
  memport_r.ready := memport_r_ready

  val memport_w = IO(new Bundle {
    val addr = Input(UInt(axi4param.addrWidth.W))
    val data = Input(UInt(axi4param.dataWidth.W))
    val cache = Input(UInt(4.W))
    val enable = Input(Bool())
    val ready = Output(Bool())
  })

  val memport_w_addr = RegInit(0.U(axi4param.addrWidth.W))
  val memport_w_data = RegInit(0.U(axi4param.dataWidth.W))
  val memport_w_ready = RegInit(0.B)

  memport_w.ready := memport_w_ready

  val axiuser = IO(new Bundle {
    val ar = if (axi4param.userWidth.aruser > 0) {
      Input(UInt(axi4param.userWidth.aruser.W))
    }
    val aw = if (axi4param.userWidth.awuser > 0) {
      Input(UInt(axi4param.userWidth.awuser.W))
    }
  })

  M_AXI.aruser match {
    case aruser: UInt => aruser := axiuser.ar.asInstanceOf[UInt]
    case _ => {}
  }
  M_AXI.awuser match {
    case awuser: UInt => awuser := axiuser.aw.asInstanceOf[UInt]
    case _ => {}
  }

  // State machine registers
  val axiReadState = RegInit(AXI4ReadState.NOOP)
  val axiWriteState = RegInit(AXI4WriteState.NOOP)

  // TODO: Control this signals using current internal status
  axi_rready := 1.B
  axi_bready := 1.B

  // State machine magic goes here
  switch (axiReadState) {
    is (AXI4ReadState.NOOP) {
      axi_arvalid := 0.B
      memport_r_ready := 0.B
      when (memport_r.enable) {
        axiReadState := AXI4ReadState.ARVALID
      }
    }

    is (AXI4ReadState.ARVALID) {
      // set up address and rise ARVALID
      axi_arvalid := 1.B
      axi_arprot := 1.U
      axi_arlen := 0.U // Single beat
      axi_araddr := memport_r.addr
      axi_arcache := memport_r.cache
      axi_arid := 137.U
      axiReadState := AXI4ReadState.ARREADY
    }

    is (AXI4ReadState.ARREADY) {
      // Wait for ARREADY signal and move to next state
      when (M_AXI.arready) {
        axi_arvalid := 0.B
        axiReadState := AXI4ReadState.RVALID
      }
    }

    is (AXI4ReadState.RVALID) {
      // Wait for RVALID signal
      when (M_AXI.rvalid && M_AXI.rready) {
        // Sample everything
        memport_r_data := M_AXI.rdata
        memport_r_ready := 1.B

        axiReadState := AXI4ReadState.RREADY
      }
    }

    is (AXI4ReadState.RREADY) {
      memport_r_ready := 0.B
      when (axi_arlen === 0.U) {
        axiReadState := AXI4ReadState.NOOP
      }.otherwise {
        axi_arlen := axi_arlen - 1.U
        axiReadState := AXI4ReadState.RVALID
      }
    }
  }

  switch (axiWriteState) {
    is (AXI4WriteState.NOOP) {
      axi_awvalid := 0.B
      memport_w_ready := 0.B
      axi_wlast := 0.B
      when (memport_w.enable) {
        memport_w_addr := memport_w.addr
        memport_w_data := memport_w.data
        axiWriteState := AXI4WriteState.AWVALID
      }
    }

    is (AXI4WriteState.AWVALID) {
      axi_awsize := "h2".U // 32-bit

      // Set up address stuff.
      // rise AWVALID
      axi_awvalid := 1.B
      axi_awaddr := memport_w_addr
      axi_awcache := memport_w.cache
      axi_awid := 137.U
      axi_awlen := 0.U
      axi_awprot := 1.U
      axi_awburst := 1.U // INCR

      axiWriteState := AXI4WriteState.AWREADY
    }

    is (AXI4WriteState.AWREADY) {
      when (M_AXI.awready) {
        axi_awvalid := 0.B
        axiWriteState := AXI4WriteState.WVALID
      }
    }

    is (AXI4WriteState.WVALID) {
      // TODO: FIXME
      // Always send 0b1111 on wstrb (all 4 bytes are valid)
      axi_wstrb := "hFF".U

      // AXI3 specific but write value to it anyway
      // FIRRTL optimizer will strip out register
      // when it thinks it is not needed.
      axi_wid := 137.U
      // TODO: Wait for the data and rise WVALID
      axi_wdata := memport_w_data
      axi_wvalid := 1.B
      when (axi_awlen === 0.U) {
        axi_wlast := 1.B
      }
      axiWriteState := AXI4WriteState.WREADY
    }

    is (AXI4WriteState.WREADY) {
      when (M_AXI.wready) {
        // TODO: Sample the data here
        axi_wvalid := 0.B
        axi_wlast := 0.B
        // TODO: Optimize meeeee

        when (axi_awlen === 0.U) {
          axiWriteState := AXI4WriteState.BVALID
        }.otherwise {
          axi_awlen := axi_awlen - 1.U
          axiWriteState := AXI4WriteState.WVALID
        }
      }
    }

    is (AXI4WriteState.BVALID) {
      when (M_AXI.bvalid && M_AXI.bready) {
        axiWriteState := AXI4WriteState.BREADY
      }
    }

    is (AXI4WriteState.BREADY) {
      memport_w_ready := 1.B
      when (memport_w.enable === 0.U) {
        axiWriteState := AXI4WriteState.NOOP
      }
    }
  }
}
