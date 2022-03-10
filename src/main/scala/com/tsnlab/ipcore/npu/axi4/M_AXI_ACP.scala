package com.tsnlab.ipcore.npu.axi4

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

class M_AXI_ACP(param: AXI4Param) extends Module {
  val M_AXI_ACP = IO(new Bundle {
    Input(UInt(3.W))
  })
}
