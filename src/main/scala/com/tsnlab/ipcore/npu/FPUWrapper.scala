package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.util.{switch, is}
import chisel3.experimental.ChiselEnum
import com.tsnlab.ipcore.axi4.AXI4Param
import com.tsnlab.ipcore.axi4.io.AXI4SlaveBundle
import com.tsnlab.ipcore.axi4.io.AXI4MasterBundle
import com.tsnlab.ipcore.axi4.M_AXI
import com.tsnlab.ipcore.axi4.S_AXI
import chisel3.util.MuxLookup
import chisel3.util.Cat

object FPUProcessState extends ChiselEnum {
  val READY    = Value
  val FETCH01A = Value
  val FETCH01D = Value
  val FETCH02A = Value
  val FETCH02D = Value
  val PROCESS  = Value
  val BUBBLE   = Value
  val DONE     = Value
}

class FPUWrapper(
  axi4MasterParam: AXI4Param,
  axi4SlaveParam: AXI4Param,
) extends Module {
  // For now, hardcode FPU parameter
  val mantissa = 23
  val exponent = 8

  val M_AXI = IO(new AXI4MasterBundle(axi4MasterParam))
  val S_AXI = IO(new AXI4SlaveBundle(axi4SlaveParam))
  val debug = IO(new Bundle{
    val led = Output(UInt(4.W))
    val busy = Output(Bool())
  })

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

  when (s_axi.REGMEM.we) {
    for (i <- 0 to 3) {
      when (s_axi.REGMEM.addr(3, 2) === i.U) {
        regvec(i) := s_axi.REGMEM.data
      }
    }
  }

  // TODO: Clean me up!
  s_axi.REGMEM_R.data := MuxLookup(s_axi.REGMEM_R.addr(3,2), "h0000_0000".U, Array(
    0.U -> regvec(0),
    1.U -> regvec(1),
    2.U -> regvec(2),
    3.U -> regvec(3),
  ))

  val flagwire     = Wire(Bits(8.W))
  val opcodewire   = Wire(UInt(8.W))
  val src1addrwire = Wire(UInt(axi4SlaveParam.dataWidth.W))
  val src2addrwire = Wire(UInt(axi4SlaveParam.dataWidth.W))
  val dstaddrwire  = Wire(UInt(axi4SlaveParam.dataWidth.W))

  flagwire     := regvec(0)(7,0)
  opcodewire   := regvec(0)(15,8)
  src1addrwire := regvec(1)
  src2addrwire := regvec(2)
  dstaddrwire  := regvec(3)

  //// Hook up S_AXI to our tiny, cute memory
  //when (s_axi.REGMEM.we) {
  //  val addrwire = Wire(UInt(4.W)) // TODO: Un-hardcode me.
  //  addrwire := s_axi.MEMIO.addr(6, 2) // Cut last 2 bit.
  //  mmioreg.write(addrwire, s_axi.MEMIO.data)
  //}

  // Some awesome state machine to handle the fault
  // Read DRAM from bus master 

  // clocktree module
  val clktree = Module(new ClockTree())

  // FPU module
  // FPU uses 4 times slower clock, bus 200MHz, clock: 50MHz
  val fpu = withClock(clktree.clko.div4.asClock) {
    Module(new FPU(exponent, mantissa))
  }

  //val fpu = Module(new FPU(exponent, mantissa))

  val fpuState = RegInit(FPUProcessState.READY)
  
  val fpu_data_a = RegInit(0.U(32.W))
  val fpu_data_b = RegInit(0.U(32.W))

  fpu.data.a := fpu_data_a
  fpu.data.b := fpu_data_b
  fpu.control.op := opcodewire(1,0)
  
  // Register def
  val fpu_i_valid = RegInit(0.B)

  fpu.control.i_valid := fpu_i_valid
  fpu.control.o_ready := 1.B

  debug.led := fpuState.asUInt()
  debug.busy := regvec(0)(1)

  switch (fpuState) {
    is (FPUProcessState.READY) {
      when (flagwire(0) === 1.B && fpu.control.i_ready) {
        regvec(0) := Cat(regvec(0)(31,2), 1.B, regvec(0)(0))
        fpuState := FPUProcessState.FETCH01A
      }
    }

    is (FPUProcessState.FETCH01A) {
      memport_r_addr := src1addrwire
      memport_r_enable := 1.B
      fpuState := FPUProcessState.FETCH01D
    }

    is (FPUProcessState.FETCH01D) {
      // Do data fetch
      memport_r_enable := 0.B
      when (m_axi.memport_r.ready) {
        fpu_data_a := m_axi.memport_r.data
        fpuState := FPUProcessState.FETCH02A
      }
    }

    is (FPUProcessState.FETCH02A) {
      memport_r_addr := src2addrwire
      memport_r_enable := 1.B
      fpuState := FPUProcessState.FETCH02D
    }

    is (FPUProcessState.FETCH02D) {
      // Do data fetch
      memport_r_enable := 0.B
      when (m_axi.memport_r.ready) {
        fpu_data_b := m_axi.memport_r.data
        fpu_i_valid := 1.B
        fpuState := FPUProcessState.PROCESS
      }
    }

    is (FPUProcessState.PROCESS) {
      // Bubble one cycle
      fpuState := FPUProcessState.BUBBLE
    }

    is (FPUProcessState.BUBBLE) {
      // wait the signal
      when (fpu.control.o_valid) {
        fpuState := FPUProcessState.DONE
        fpu_i_valid := 0.B
        memport_w_addr := dstaddrwire
        memport_w_data := fpu.data.y
        memport_w_enable := 1.B
      }
    }

    is (FPUProcessState.DONE) {
      when (m_axi.memport_w.ready) {
        regvec(0) := Cat(regvec(0)(31,2), 0.B, 0.B)
        memport_w_enable := 0.B
        fpuState := FPUProcessState.READY
      }
    }
  }
}
