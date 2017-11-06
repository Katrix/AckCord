/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
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
package net.katsstuff.ackcord

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.{AbstractMap, LongMap, MapLike}
import scala.collection.mutable

import net.katsstuff.ackcord.data.SnowflakeType

//A wrapper around a LongMap which allows a nice API. We overwrite everything LongMap overrides.
class SnowflakeMap[K, +V](private val inner: LongMap[V])
    extends AbstractMap[SnowflakeType[K], V]
    with Map[SnowflakeType[K], V]
    with MapLike[SnowflakeType[K], V, SnowflakeMap[K, V]] {
  type Key = SnowflakeType[K]

  private def keyToSnowflake(k: Long): Key = SnowflakeType[K](k)

  override def empty: SnowflakeMap[K, V] = new SnowflakeMap(inner.empty)

  override def toList: List[(Key, V)] = inner.toList.map {
    case (k, v) => (keyToSnowflake(k), v)
  }

  override def iterator: Iterator[(Key, V)] = inner.iterator.map {
    case (k, v) => (keyToSnowflake(k), v)
  }

  override final def foreach[U](f: ((Key, V)) => U): Unit =
    inner.foreach {
      case (k, v) => f(keyToSnowflake(k), v)
    }

  override def keysIterator: Iterator[Key] = inner.keysIterator.map(keyToSnowflake)

  /**
    * Loop over the keys of the map. The same as keys.foreach(f), but may
    * be more efficient.
    *
    * @param f The loop body
    */
  final def foreachKey(f: Key => Unit): Unit = inner.foreachKey(k => f(keyToSnowflake(k)))

  override def valuesIterator: Iterator[V] = inner.valuesIterator

  /**
    * Loop over the values of the map. The same as values.foreach(f), but may
    * be more efficient.
    *
    * @param f The loop body
    */
  final def foreachValue(f: V => Unit): Unit = inner.foreachValue(f)

  override def stringPrefix = "SnowflakeMap"

  override def isEmpty: Boolean = inner.isEmpty

  override def filter(p: ((Key, V)) => Boolean): SnowflakeMap[K, V] =
    new SnowflakeMap(inner.filter {
      case (k, v) => p(keyToSnowflake(k), v)
    })

  def transform[S](f: (Key, V) => S): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.transform[S] {
      case (k, v) => f(keyToSnowflake(k), v)
    })

  override final def size: Int = inner.size

  override final def get(key: Key): Option[V] = inner.get(key)

  override final def getOrElse[V1 >: V](key: Key, default: => V1): V1 = inner.getOrElse(key, default)

  override final def apply(key: Key): V = inner.apply(key)

  override def +[V1 >: V](kv: (Key, V1)): SnowflakeMap[K, V1] = new SnowflakeMap(inner.updated(kv._1, kv._2))

  override def updated[V1 >: V](key: Key, value: V1): SnowflakeMap[K, V1] =
    new SnowflakeMap(inner.updated(key, value))

  /**
    * Updates the map, using the provided function to resolve conflicts if the key is already present.
    *
    * Equivalent to
    * {{{
    *   this.get(key) match {
    *     case None => this.update(key, value)
    *     case Some(oldvalue) => this.update(key, f(oldvalue, value)
    *   }
    * }}}
    *
    * @tparam S     The supertype of values in this `SnowflakeMap`.
    * @param key    The key to update.
    * @param value  The value to use if there is no conflict.
    * @param f      The function used to resolve conflicts.
    * @return       The updated map.
    */
  def updateWith[S >: V](key: Key, value: S, f: (V, S) => S): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.updateWith(key, value, f))

  override def -(key: Key): SnowflakeMap[K, V] = new SnowflakeMap(inner - key)

  /**
    * A combined transform and filter function. Returns an `SnowflakeMap` such that
    * for each `(key, value)` mapping in this map, if `f(key, value) == None`
    * the map contains no mapping for key, and if `f(key, value)`.
    *
    * @tparam S    The type of the values in the resulting `SnowflakeMap`.
    * @param f     The transforming function.
    * @return      The modified map.
    */
  def modifyOrRemove[S](f: (Key, V) => Option[S]): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.modifyOrRemove {
      case (k, v) => f(keyToSnowflake(k), v)
    })

  /**
    * Forms a union map with that map, using the combining function to resolve conflicts.
    *
    * @tparam S      The type of values in `that`, a supertype of values in `this`.
    * @param that    The map to form a union with.
    * @param f       The function used to resolve conflicts between two mappings.
    * @return        Union of `this` and `that`, with identical key conflicts resolved using the function `f`.
    */
  def unionWith[S >: V](that: SnowflakeMap[Key, S], f: (Key, S, S) => S): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.unionWith[S](that.inner, (l, s1, s2) => f(keyToSnowflake(l), s1, s2)))

  /**
    * Forms the intersection of these two maps with a combining function. The
    * resulting map is a map that has only keys present in both maps and has
    * values produced from the original mappings by combining them with `f`.
    *
    * @tparam S      The type of values in `that`.
    * @tparam R      The type of values in the resulting `SnowflakeMap`.
    * @param that    The map to intersect with.
    * @param f       The combining function.
    * @return        Intersection of `this` and `that`, with values for identical keys produced by function `f`.
    */
  def intersectionWith[S, R](that: SnowflakeMap[Key, S], f: (Key, V, S) => R): SnowflakeMap[K, R] =
    new SnowflakeMap(inner.intersectionWith[S, R](that.inner, (l, v, s) => f(keyToSnowflake(l), v, s)))

  /**
    * Left biased intersection. Returns the map that has all the same mappings as this but only for keys
    * which are present in the other map.
    *
    * @tparam R      The type of values in `that`.
    * @param that    The map to intersect with.
    * @return        A map with all the keys both in `this` and `that`, mapped to corresponding values from `this`.
    */
  def intersection[R](that: SnowflakeMap[K, R]): SnowflakeMap[K, V] = new SnowflakeMap(inner.intersection(that.inner))

  def ++[S >: V](that: SnowflakeMap[K, S]): SnowflakeMap[K, S] = new SnowflakeMap(inner ++ that.inner)

  final def firstKey: Key = keyToSnowflake(inner.firstKey)

  final def lastKey: Key = keyToSnowflake(inner.lastKey)
}
object SnowflakeMap {

  def empty[A, B]: SnowflakeMap[A, B] = new SnowflakeMap(LongMap.empty)
  def singleton[A, B](key: SnowflakeType[A], value: B): SnowflakeMap[A, B] =
    new SnowflakeMap(LongMap.singleton(key, value))
  def apply[A, B](elems: (SnowflakeType[A], B)*): SnowflakeMap[A, B] =
    new SnowflakeMap(LongMap.apply(elems: _*))

  implicit def canBuildFrom[S, A, B]: CanBuildFrom[SnowflakeMap[S, A], (SnowflakeType[S], B), SnowflakeMap[S, B]] =
    new CanBuildFrom[SnowflakeMap[S, A], (SnowflakeType[S], B), SnowflakeMap[S, B]] {
      override def apply(from: SnowflakeMap[S, A]): mutable.Builder[(SnowflakeType[S], B), SnowflakeMap[S, B]] = apply()
      override def apply(): mutable.Builder[(SnowflakeType[S], B), SnowflakeMap[S, B]] =
        new mutable.MapBuilder[SnowflakeType[S], B, SnowflakeMap[S, B]](empty)
    }
}
