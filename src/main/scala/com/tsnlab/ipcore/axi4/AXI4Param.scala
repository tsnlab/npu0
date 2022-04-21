package com.tsnlab.ipcore.axi4

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.internal.firrtl.Width

object AXIVariant extends Enumeration {
  type AXIVariant = Value
  val AXI3        = Value
  val AXI3_LITE   = Value
  val AXI4        = Value
  val AXI4_LITE   = Value
  val AXI5        = Value
  val AXI5_LITE   = Value
}

import AXIVariant.AXIVariant

case class AXIUserConfig (
  awuser: Int,
  aruser: Int,
  wuser: Int,
  ruser: Int,
  buser: Int,
)

// TODO: Implement assertation
case class AXI4Param (
  idWidth: Int,
  addrWidth: Int,
  dataWidth: Int,
  userWidth: AXIUserConfig,
  busvariant: AXIVariant,
) {
  def getBurstWidthAsInt(): Width = {
    this.busvariant match {
      case AXIVariant.AXI3 => 4
      case AXIVariant.AXI3_LITE => 4
      case AXIVariant.AXI4 => 8
      case AXIVariant.AXI4_LITE => 8
    }
  }

  def getBurstWidth(): Width = {
    this.getBurstWidthAsInt().W
  }
  
  def getStrbWidth(): Width = {
    ((this.dataWidth + 7) / 8).W // See A10.3.4
  }
}

// AXI4 state machine definition
object AXI4WriteState extends ChiselEnum {
  val NOOP    = Value
  val AWVALID = Value
  val AWREADY = Value
  val WVALID  = Value
  val WREADY  = Value
  val BVALID  = Value
  val BREADY  = Value
}

object AXI4ReadState extends ChiselEnum {
  val NOOP    = Value
  val ARVALID = Value
  val ARREADY = Value
  val RVALID  = Value
  val RREADY  = Value
}
