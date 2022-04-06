package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import com.tsnlab.ipcore.npu.FPUWrapper
import com.tsnlab.ipcore.axi4.{M_AXI, S_AXI, AXI4Param}
import com.tsnlab.ipcore.axi4.{AXIVariant, AXIUserConfig}

object VerilogEmitter extends App {
  // Configure the IP using some values
  val axi4MasterParam = AXI4Param (
    idWidth = 3,
    addrWidth = 32,
    dataWidth = 64,
    //userWidth = Some(AXIUserConfig(
    //  awuser = 5,
    //  aruser = 5,
    //  wuser  = 0,
    //  ruser  = 0,
    //  buser  = 0,
    //)),
    userWidth = None,
    busvariant = AXIVariant.AXI3, // ZYNQ-7000 Uses AXI3
  )

  val axi4SlaveParam = AXI4Param (
    idWidth = 12,
    addrWidth = 32,
    dataWidth = 32,
    userWidth = None,
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
