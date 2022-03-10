package com.tsnlab.ipcore.npu.axi4

import chisel3.experimental.ChiselEnum

// TODO: Implement assertation
case class AXI4Param (
  idWidth: Int,
  addrWidth: Int,
  dataWidth: Int,
  burstWidth: Int, // For AXI3, set it to 4. AXI4, set it to 8.
  awuserWidth: Int,
  aruserWidth: Int,
  wuserWidth: Int,
  ruserWidth: Int,
  buserWidth: Int,
  isLite: Boolean,
)

// AXI4 state machine definition
object AXI4WriteState extends ChiselEnum {
  val AWVALID = Value
  val AWREADY = Value
  val WVALID  = Value
  val WREADY  = Value
  val BVALID  = Value
  val BREADY  = Value
}

object AXI4ReadState extends ChiselEnum {
  val ARVALID = Value
  val ARREADY = Value
  val RVALID  = Value
  val RREADY  = Value
}
