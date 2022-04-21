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

import freechips.asyncqueue.{AsyncQueue, AsyncQueueParams}

object FPUProcessState extends ChiselEnum {
  val READY    = Value
  val FETCH01A = Value
  val FETCH01D = Value
  val FETCH02A = Value
  val FETCH02D = Value
  val PROCESS01= Value
  val PROCESS02= Value
  val DONE     = Value
}

object FPUWriteState extends ChiselEnum {
  val READY = Value
  val WRITE = Value
  val DONE  = Value
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
  m_axi.memport_r.cache := "b0010".U

  val memport_w_addr = RegInit(0.U(32.W))  
  val memport_w_data = RegInit(0.U(32.W))
  val memport_w_enable = RegInit(0.B)
  m_axi.memport_w.addr := memport_w_addr
  m_axi.memport_w.data := memport_w_data
  m_axi.memport_w.enable := memport_w_enable
  m_axi.memport_w.cache := "b0010".U
  
  // TODO: Temporal value, fixme
  m_axi.memport_w.burstlen := 0.U
  m_axi.memport_r.burstlen := 0.U

  m_axi.axiuser.ar match {
    case ar: UInt => ar := "b00001".U
    case _ => {}
  }
  m_axi.axiuser.aw match {
    case aw: UInt => aw := "b00001".U
    case _ => {}
  }

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
  val fpuclk = Wire(Bool())

  fpuclk := clktree.clko.div4

  // FPU module
  // FPU uses 4 times slower clock, bus 200MHz, clock: 50MHz
  val asyncFpu = withClockAndReset(fpuclk.asClock, clktree.clko.rst4) {
    Module(new AsyncFPU(exponent, mantissa))
  }

  //val fpu = Module(new FPU(exponent, mantissa))
  val queue_a = Module(new AsyncQueue(UInt(32.W), new AsyncQueueParams()))
  val queue_b = Module(new AsyncQueue(UInt(32.W), new AsyncQueueParams()))
  val queue_y = Module(new AsyncQueue(UInt(32.W), new AsyncQueueParams()))

  queue_a.io.enq_reset := reset
  queue_a.io.enq_clock := clock
  queue_a.io.deq_reset := reset
  queue_a.io.deq_clock := fpuclk.asClock

  queue_b.io.enq_reset := reset
  queue_b.io.enq_clock := clock
  queue_b.io.deq_reset := reset
  queue_b.io.deq_clock := fpuclk.asClock

  queue_y.io.enq_reset := reset
  queue_y.io.enq_clock := fpuclk.asClock
  queue_y.io.deq_reset := reset
  queue_y.io.deq_clock := clock

  queue_a.io.enq.bits := m_axi.memport_r.data
  queue_b.io.enq.bits := m_axi.memport_r.data
  queue_a.io.enq.valid := 0.B
  queue_b.io.enq.valid := 0.B
  queue_y.io.deq.ready := 1.B

  // Temporal
  //queue_a.io.deq.ready := 0.B
  //queue_b.io.deq.ready := 0.B
  //queue_y.io.enq.valid := 0.B
  //queue_y.io.enq.bits := 0.U

  asyncFpu.data.a <> queue_a.io.deq
  asyncFpu.data.b <> queue_b.io.deq
  queue_y.io.enq <> asyncFpu.data.y

  val fpuState = RegInit(FPUProcessState.READY)
  val fpuWriteState = RegInit(FPUWriteState.READY)
  
  val fpu_data_a = RegInit(0.U(32.W))
  val fpu_data_b = RegInit(0.U(32.W))

  //fpu.data.a := queue_a.io.deq.bits
  //fpu.data.b := queue_b.io.deq.bits
  //fpu.control.op := opcodewire(1,0)
  asyncFpu.control.op := opcodewire(1,0)
  
  // Register def
  val fpu_i_valid = RegInit(0.B)
  val fpu_o_ready = RegInit(0.B)

  debug.led := fpuState.asUInt()
  debug.busy := regvec(0)(1)

  switch (fpuState) {
    is (FPUProcessState.READY) {
      when (flagwire(0) === 1.B) {
        regvec(0) := Cat(regvec(0)(31,2), 1.B, regvec(0)(0))
        when (queue_a.io.enq.ready && queue_b.io.enq.ready) {
          fpuState := FPUProcessState.FETCH01A
        }
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
        queue_a.io.enq.valid := 1.B
        when (queue_a.io.enq.ready === 1.B) {
          fpuState := FPUProcessState.FETCH02A
        }
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
        queue_b.io.enq.valid := 1.B
        fpuState := FPUProcessState.DONE
      }
    }

    is (FPUProcessState.DONE) {
      when (!regvec(0)(0)) {
        fpuState := FPUProcessState.READY
      }
    }
  }

  switch (fpuWriteState) {
    is (FPUWriteState.READY) {
      memport_w_enable := 0.B
      when (m_axi.memport_w.ready) {
        fpuWriteState := FPUWriteState.WRITE
      }
    }

    is (FPUWriteState.WRITE) {
      when (queue_y.io.deq.valid) {
        memport_w_addr := dstaddrwire
        memport_w_data := queue_y.io.deq.bits
        memport_w_enable := 1.B
        fpuWriteState := FPUWriteState.DONE
      }
    }

    is (FPUWriteState.DONE) {
      queue_y.io.deq.ready := 0.B
      memport_w_enable := 0.B
      regvec(0) := Cat(regvec(0)(31,2), 0.B, 0.B)
      //when (fpu.control.i_ready) {
        fpuWriteState := FPUWriteState.READY
      //}
    }
  }
}
