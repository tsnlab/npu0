package com.tsnlab.ipcore.util

import scala.math

object BitUtil {
  private def log2(x: Double): Double = {
    math.log10(x) / math.log10(2.0)
  }

  def getBitWidth(maxval: Int): Int = {
    math.ceil(this.log2(maxval)).toInt
  }

  def isPowerOfTwo(value: Int): Boolean = {
    if (value < 0) return false

    value match {
      case 0 => true
      case 2 => true
      case x => x % 2 match {
        case 0 => isPowerOfTwo(x / 2)
        case 1 => false
      }
    }
  }
}
