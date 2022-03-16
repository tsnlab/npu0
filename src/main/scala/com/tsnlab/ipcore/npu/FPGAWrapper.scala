package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import com.tsnlab.ipcore.axi4.AXI4Param
import com.tsnlab.ipcore.axi4.io.AXI4SlaveBundle
import com.tsnlab.ipcore.axi4.io.AXI4MasterBundle
import com.tsnlab.ipcore.axi4.M_AXI
import com.tsnlab.ipcore.axi4.S_AXI

object FPUProcessState extends ChiselEnum {
  val READY   = Value
  val FETCH   = Value
  val PROCESS = Value
  val DONE    = Value
}

class FPGAWrapper(
  axi4MasterParam: AXI4Param,
  axi4SlaveParam: AXI4Param,
) extends Module {
  val M_AXI = IO(new AXI4MasterBundle(axi4MasterParam))
  val S_AXI = IO(new AXI4SlaveBundle(axi4SlaveParam))

  // Our tiny cute RAM for MMIO regs
  val mmioreg = SyncReadMem(16, UInt(axi4SlaveParam.dataWidth.W))

  val m_axi = Module(new M_AXI(axi4MasterParam));
  val s_axi = Module(new S_AXI(axi4SlaveParam));

  // Connect them
  s_axi.S_AXI <> S_AXI
  m_axi.M_AXI <> M_AXI

  // Hook up S_AXI to our tiny, cute memory
  when (s_axi.MEMIO.we) {
    val addrwire = Wire(UInt(4.W)) // TODO: Un-hardcode me.
    addrwire := s_axi.MEMIO.addr(6, 2) // Cut last 2 bit.
    mmioreg.write(addrwire, s_axi.MEMIO.data)
  }

  // Some awesome state machine to handle the fault
  // Read DRAM from bus master 

  val fpuState = RegInit(FPUProcessState.READY)

  switch (fpuState) {
    is (FPUProcessState.READY) {
      fpuState := FPUProcessState.FETCH
    }

    is (FPUProcessState.FETCH) {
      fpuState := FPUProcessState.PROCESS
    }

    is (FPUProcessState.PROCESS) {
      fpuState := FPUProcessState.DONE
    }

    is (FPUProcessState.DONE) {
      fpuState := FPUProcessState.READY
    }
  }
}
