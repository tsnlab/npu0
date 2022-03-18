package com.tsnlab.ipcore.npu.util

object BusState extends Enumeration {
  type BusState = Value
  val ARVALID = Value
  val ARREADY = Value
  val RVALID  = Value
  val RREADY  = Value
  val AWVALID = Value
  val AWREADY = Value
  val WVALID  = Value
  val WREADY  = Value
  val BVALID  = Value
  val BREADY  = Value
  val NOOP    = Value
}

