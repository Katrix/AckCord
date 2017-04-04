/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.akkacord

import net.katsstuff.akkacord.data.{Snowflake, User}
import shapeless.labelled._
import shapeless._
import shapeless.ops.hlist.Mapper

package object http {

  object mapPartialId extends mapPartialId
  trait mapPartialId extends FieldPoly with atOption {
    implicit def atId = atField[Snowflake]('id)(identity)
  }

  trait atOption extends normal {
    implicit def atOption[K, V] = at[FieldType[K, Option[V]]]((v: Option[V]) => field[K](v))
  }

  trait normal extends Poly1 {
    implicit def normal[K, V] = at[FieldType[K, V]]((v: V) => field[K](Option(v)))
  }

  val userGen           = LabelledGeneric[User]
  val partialUserMapper = Mapper[mapPartialId.type, userGen.Repr]
  type PartialUser = partialUserMapper.Out
}
