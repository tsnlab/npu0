package com.tsnlab.ipcore.axi4.io

import chisel3._
import com.tsnlab.ipcore.axi4.{AXI4Param, AXIVariant, AXIUserConfig}

// NOTE: Reference doc: http://www.gstitt.ece.ufl.edu/courses/fall15/eel4720_5721/labs/refs/AXI4_specification.pdf

object BusType extends Enumeration {
  type BusType = Value
  val M_AW     = Value
  val M_AR     = Value
  val M_W      = Value
  val M_R      = Value
  val M_B      = Value
  val S_AW     = Value
  val S_AR     = Value
  val S_W      = Value
  val S_R      = Value
  val S_B      = Value
}

import BusType.BusType

object BusDir extends Enumeration {
  type BusDir = Value
  val In      = Value
  val Out     = Value
}

import BusDir.BusDir

object AXIIOHelper {
  def getUserBus(busType: BusType, config: AXIUserConfig) = {
    val (busWidth: Int, busDir: BusDir) = busType match {
      case BusType.M_AW => (config.awuser, BusDir.Out)
      case BusType.S_AW => (config.awuser, BusDir.In)
      case BusType.M_AR => (config.aruser, BusDir.Out)
      case BusType.S_AR => (config.aruser, BusDir.In)
      case BusType.M_W  => (config.wuser, BusDir.Out)
      case BusType.S_W  => (config.wuser, BusDir.In)
      case BusType.M_R  => (config.ruser, BusDir.In)
      case BusType.S_R  => (config.ruser, BusDir.Out)
      case BusType.M_B  => (config.buser, BusDir.In)
      case BusType.S_B  => (config.buser, BusDir.Out)
    }

    if (busWidth > 0) {
      busDir match {
        case BusDir.In => Input(UInt(busWidth.W))
        case BusDir.Out => Output(UInt(busWidth.W))
      }
    }
  }
}

class AXI4MasterBundle(param: AXI4Param) extends Bundle {
  // AXI4 Master IO

  // should we use this...?
  // val clk     = Input(Clock())

  // AW channel
  val awaddr  = Output(UInt(param.addrWidth.W))
  val awlen   = Output(UInt(param.getBurstWidth()))
  val awsize  = Output(UInt(3.W)) // TODO: Make it configurable?
  val awburst = Output(UInt(2.W))
  val awlock  = Output(UInt(2.W))
  val awcache = Output(UInt(4.W))
  val awprot  = Output(UInt(3.W))
  val awid    = Output(UInt(param.idWidth.W))
  val awqos   = Output(UInt(4.W)) // See A8.1.1
  val awvalid = Output(Bool())
  val awready = Input(Bool())
  val awuser  = AXIIOHelper.getUserBus(BusType.M_AW, param.userWidth)

  // W channel
  val wdata   = Output(UInt(param.dataWidth.W))
  // TODO: FIXME: is it right to calculate like this?
  val wstrb   = Output(UInt(((param.dataWidth + 7) / 8).W)) // See A10.3.4
  val wlast   = Output(Bool())
  val wvalid  = Output(Bool())
  val wready  = Input(Bool())
  val wid = if (param.busvariant == AXIVariant.AXI3) {
    Output(UInt(param.idWidth.W))
  }
  val wuser  = AXIIOHelper.getUserBus(BusType.M_W, param.userWidth)

  // AR channel
  val araddr  = Output(UInt(param.addrWidth.W))
  val arlen   = Output(UInt(param.getBurstWidth()))
  val arsize  = Output(UInt(3.W))
  val arburst = Output(UInt(2.W))
  val arlock  = Output(UInt(2.W))
  val arcache = Output(UInt(4.W))
  val arprot  = Output(UInt(3.W))
  val arid    = Output(UInt(param.idWidth.W))
  val arqos   = Output(UInt(4.W)) // See A8.1.1
  val arvalid = Output(Bool())
  val arready = Input(Bool())
  val aruser  = AXIIOHelper.getUserBus(BusType.M_AR, param.userWidth)

  // R channel
  val rdata   = Input(UInt(param.dataWidth.W))
  val rid     = Input(UInt(param.idWidth.W))
  val rresp   = Input(UInt(2.W))
  val rlast   = Input(Bool())
  val rvalid  = Input(Bool())
  val rready  = Output(Bool())

  // B channel
  val bid     = Input(UInt(param.idWidth.W))
  val bresp   = Input(UInt(2.W)) // TODO: Read the AXI4 spec
  val bvalid  = Input(Bool())
  val bready  = Output(Bool())
}

class AXI4SlaveBundle(param: AXI4Param) extends Bundle {
  // AXI4 Slave IO

  //val clk     = Input(Clock())

  // AW channel
  val awaddr  = Input(UInt(param.addrWidth.W))
  val awlen   = Input(UInt(param.getBurstWidth()))
  val awsize  = Input(UInt(3.W)) // TODO: Make it configurable?
  val awburst = Input(UInt(2.W))
  val awlock  = Input(UInt(2.W))
  val awcache = Input(UInt(4.W))
  val awprot  = Input(UInt(3.W))
  val awid    = Input(UInt(param.idWidth.W))
  val awqos   = Input(UInt(4.W)) // See A8.1.1
  val awvalid = Input(Bool())
  val awready = Output(Bool())
  val awuser  = AXIIOHelper.getUserBus(BusType.S_AW, param.userWidth)

  // W channel
  val wdata   = Input(UInt(param.dataWidth.W))
  // TODO: FIXME: is it right to calculate like this?
  val wstrb   = Input(UInt(param.getStrbWidth()))
  val wlast   = Input(Bool())
  val wvalid  = Input(Bool())
  val wready  = Output(Bool())
  if (param.busvariant == AXIVariant.AXI3) {
    val wid   = Input(UInt(param.idWidth.W))
  }
  val wuser  = AXIIOHelper.getUserBus(BusType.S_W, param.userWidth)

  // AR channel
  val araddr  = Input(UInt(param.addrWidth.W))
  val arlen   = Input(UInt(param.getBurstWidth()))
  val arsize  = Input(UInt(3.W))
  val arburst = Input(UInt(2.W))
  val arlock  = Input(UInt(2.W))
  val arcache = Input(UInt(4.W))
  val arprot  = Input(UInt(3.W))
  val arid    = Input(UInt(param.idWidth.W))
  val arqos   = Input(UInt(4.W)) // See A8.1.1
  val arvalid = Input(Bool())
  val arready = Output(Bool())
  val aruser  = AXIIOHelper.getUserBus(BusType.S_AR, param.userWidth)

  // R channel
  val rdata   = Output(UInt(param.dataWidth.W))
  val rid     = Output(UInt(param.idWidth.W))
  val rresp   = Output(UInt(2.W))
  val rlast   = Output(Bool())
  val rvalid  = Output(Bool())
  val rready  = Input(Bool())
  val ruser  = AXIIOHelper.getUserBus(BusType.S_R, param.userWidth)

  // B channel
  val bid     = Output(UInt(param.idWidth.W))
  val bresp   = Output(UInt(2.W)) // TODO: Read the AXI4 spec
  val bvalid  = Output(Bool())
  val bready  = Input(Bool())
  val buser  = AXIIOHelper.getUserBus(BusType.S_B, param.userWidth)
}

//class AXI_AWID
