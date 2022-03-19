package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.npu.FPUWrapper
import com.tsnlab.ipcore.axi4.{M_AXI, S_AXI, AXI4Param}
import com.tsnlab.ipcore.axi4.AXIVariant

object VerilogEmitter extends App {
  // Configure the IP using some values
  val axi4MasterParam = AXI4Param (
    idWidth = 6,
    addrWidth = 32,
    dataWidth = 32,
    awuserWidth = 2,
    aruserWidth = 2,
    wuserWidth = 0,
    ruserWidth = 0,
    buserWidth = 0,
    busvariant = AXIVariant.AXI3, // ZYNQ-7000 Uses AXI3
  )

  val axi4SlaveParam = AXI4Param (
    idWidth = 12,
    addrWidth = 32,
    dataWidth = 32,
    awuserWidth = 2,
    aruserWidth = 2,
    wuserWidth = 0,
    ruserWidth = 0,
    buserWidth = 0,
    busvariant = AXIVariant.AXI3, // ZYNQ-7000 Uses AXI3
  )

  // Emit the verilog
  val chiselOpts = Array("-td", "vout")
  (new ChiselStage).emitVerilog(new M_AXI(axi4SlaveParam), chiselOpts)
  (new ChiselStage).emitVerilog(new S_AXI(axi4SlaveParam), chiselOpts)
  (new ChiselStage).emitVerilog(new FPUWrapper(axi4MasterParam, axi4SlaveParam), chiselOpts)

  // generate graph files for circuit visualization
  val elkOpts = Array("-td", "vout", "--lowFir")
  (new layered.stage.ElkStage).execute(elkOpts,
    Seq(ChiselGeneratorAnnotation(() => new M_AXI(axi4SlaveParam)))
  )
  (new layered.stage.ElkStage).execute(elkOpts,
    Seq(ChiselGeneratorAnnotation(() => new S_AXI(axi4SlaveParam)))
  )
}
