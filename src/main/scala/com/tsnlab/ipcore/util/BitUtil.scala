package com.tsnlab.ipcore.util

import scala.math

object BitUtil {
  private def log2(x: Double): Double = {
    math.log10(x) / math.log10(2.0)
  }

  def getBitWidth(maxval: Int): Int = {
    math.ceil(this.log2(maxval)).toInt
  }
}
