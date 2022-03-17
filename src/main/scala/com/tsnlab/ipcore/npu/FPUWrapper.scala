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

class FPUWrapper(
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

  val memport_r_addr = RegInit(0.U(32.W))
  val memport_r_enable = RegInit(0.B)
  m_axi.memport_r.addr := memport_r_addr
  m_axi.memport_r.enable := memport_r_enable

  val memport_w_addr = RegInit(0.U(32.W))  
  val memport_w_data = RegInit(0.U(32.W))
  val memport_w_enable = RegInit(0.B)
  m_axi.memport_w.addr := memport_w_addr
  m_axi.memport_w.data := memport_w_data
  m_axi.memport_w.enable := memport_w_enable

  // Define our tiny tiny cute MMIO register.
  val regvec = RegInit(VecInit(Seq.fill(4)(0.U(axi4SlaveParam.dataWidth.W))))

  when (s_axi.MEMIO.we) {
    for (i <- 0 to 3) {
      when (s_axi.MEMIO.addr(1, 0) === i.U) {
        regvec(i) := s_axi.MEMIO.data
      }
    }
  }

  val flagwire    = Wire(Bits(axi4SlaveParam.dataWidth.W))  
  val srcaddrwire = Wire(UInt(axi4SlaveParam.dataWidth.W))
  val dstaddrwire = Wire(UInt(axi4SlaveParam.dataWidth.W))

  flagwire    := regvec(0)
  srcaddrwire := regvec(2)
  dstaddrwire := regvec(3)

  //// Hook up S_AXI to our tiny, cute memory
  //when (s_axi.MEMIO.we) {
  //  val addrwire = Wire(UInt(4.W)) // TODO: Un-hardcode me.
  //  addrwire := s_axi.MEMIO.addr(6, 2) // Cut last 2 bit.
  //  mmioreg.write(addrwire, s_axi.MEMIO.data)
  //}

  // Some awesome state machine to handle the fault
  // Read DRAM from bus master 

  val fpuState = RegInit(FPUProcessState.READY)
  
  val payloadbuf = RegInit(0.U(32.W))

  switch (fpuState) {
    is (FPUProcessState.READY) {
      when (flagwire(0) === 1.B) {
        regvec(1) := 1.U
        fpuState := FPUProcessState.FETCH
      }
    }

    is (FPUProcessState.FETCH) {
      // Do data fetch
      memport_r_addr := srcaddrwire
      memport_r_enable := 1.B
      when (m_axi.memport_r.ready) {
        payloadbuf := m_axi.memport_r.data
        memport_r_enable := 0.B
        fpuState := FPUProcessState.PROCESS
      }
    }

    is (FPUProcessState.PROCESS) {
      // TODO: Implement FPU wiring here
      fpuState := FPUProcessState.DONE
      memport_w_addr := dstaddrwire
      memport_w_data := payloadbuf
      memport_w_enable := 1.B
    }

    is (FPUProcessState.DONE) {
      when (m_axi.memport_w.ready) {
        regvec(1) := 0.U
        memport_w_enable := 0.B
        fpuState := FPUProcessState.READY
      }
    }
  }
}
