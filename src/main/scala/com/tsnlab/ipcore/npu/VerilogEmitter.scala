package com.tsnlab.ipcore.npu.axi4

import chisel3._
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.npu.FPGAWrapper

object VerilogEmitter extends App {
  // Configure the IP using some values
  val axiparam = AXI4Param (
    idWidth = 12,
    addrWidth = 32,
    dataWidth = 32,
    burstWidth = 4, // ZYNQ-7000 Uses AXI3
    awuserWidth = 2,
    aruserWidth = 2,
    wuserWidth = 0,
    ruserWidth = 0,
    buserWidth = 0,
    isLite = false,
  )

  // Emit the verilog
  val chiselOpts = Array("-td", "vout")
  (new ChiselStage).emitVerilog(new M_AXI(axiparam), chiselOpts)
  (new ChiselStage).emitVerilog(new S_AXI(axiparam), chiselOpts)
  (new ChiselStage).emitVerilog(new FPGAWrapper(axiparam), chiselOpts)

  // generate graph files for circuit visualization
  val elkOpts = Array("-td", "vout", "--lowFir")
  (new layered.stage.ElkStage).execute(elkOpts,
    Seq(ChiselGeneratorAnnotation(() => new M_AXI(axiparam)))
  )
  (new layered.stage.ElkStage).execute(elkOpts,
    Seq(ChiselGeneratorAnnotation(() => new S_AXI(axiparam)))
  )
}
