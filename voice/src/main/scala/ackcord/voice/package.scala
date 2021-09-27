/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ackcord

import shapeless.tag
import shapeless.tag.@@

package object voice {

  type SpeakingFlag = Long @@ SpeakingFlag.type
  object SpeakingFlag {
    private[voice] def apply(long: Long): Long @@ SpeakingFlag.type = tag[SpeakingFlag.type](long)

    def fromLong(long: Long): SpeakingFlag = SpeakingFlag(long)

    val None: SpeakingFlag = SpeakingFlag(0)

    val Microphone: SpeakingFlag = SpeakingFlag(1 << 0)
    val Soundshare: SpeakingFlag = SpeakingFlag(1 << 1)
    val Priority: SpeakingFlag   = SpeakingFlag(1 << 2)
  }
  implicit class SpeakingFlagSyntax(private val flags: SpeakingFlag) extends AnyVal {

    /**
      * Add a spealing flag to this speaking flag.
      * @param other
      *   The other speaking flag.
      */
    def ++(other: SpeakingFlag): SpeakingFlag = SpeakingFlag(flags | other)
  }
}
