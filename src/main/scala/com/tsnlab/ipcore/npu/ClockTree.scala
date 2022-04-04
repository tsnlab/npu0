package com.tsnlab.ipcore.npu

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{switch, is, MuxLookup}

class ClockTree extends Module {
  val clko = IO(new Bundle {
    val div2 = Output(Bool())
    val div4 = Output(Bool())
    val div8 = Output(Bool())
    val rst2 = Output(Bool())
    val rst4 = Output(Bool())
    val rst8 = Output(Bool())
  })

  val divreg = RegInit("b111".U(3.W))
  divreg := divreg + 1.U

  clko.div2 := divreg(0)
  clko.div4 := divreg(1)
  clko.div8 := divreg(2)

  val rstcnt = RegInit("b11111".U(5.W))
  rstcnt := rstcnt - (rstcnt(4) & 1.U)

  val rst2 = RegInit(1.B)
  val rst4 = RegInit(1.B)
  val rst8 = RegInit(1.B)

  //clko.rst2 := rst2 || reset.asBool
  //clko.rst4 := rst4 || reset.asBool
  //clko.rst8 := rst8 || reset.asBool
  clko.rst2 := rstcnt(4)
  clko.rst4 := rstcnt(4)
  clko.rst8 := rstcnt(4)

  withClock((~clko.div2).asClock) {
    rst2 := 0.B
  }
  withClock((~clko.div4).asClock) {
    rst4 := 0.B
  }
  withClock((~clko.div8).asClock) {
    rst8 := 0.B
  }
}
