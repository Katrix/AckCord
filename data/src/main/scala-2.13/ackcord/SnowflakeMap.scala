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

import scala.language.implicitConversions

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.generic.DefaultSerializationProxy
import scala.collection.immutable.{AbstractMap, LongMap, StrictOptimizedMapOps}
import scala.collection.mutable.ListBuffer
import scala.collection.{BuildFrom, Factory, View, mutable}

import ackcord.data.SnowflakeType

//A wrapper around a LongMap which allows a nice API. We overwrite everything LongMap overrides.
class SnowflakeMap[K, +V](private val inner: LongMap[V])
    extends AbstractMap[SnowflakeType[K], V]
    with StrictOptimizedMapOps[SnowflakeType[K], V, Map, SnowflakeMap[K, V]]
    with Serializable {
  type Key = SnowflakeType[K]

  override protected def fromSpecific(
      coll: IterableOnce[(SnowflakeType[K], V)] @uncheckedVariance
  ): SnowflakeMap[K, V] = {
    val b = newSpecificBuilder
    b.sizeHint(coll)
    b.addAll(coll)
    b.result()
  }

  override protected def newSpecificBuilder
      : mutable.Builder[(SnowflakeType[K], V), SnowflakeMap[K, V]] @uncheckedVariance =
    new mutable.ImmutableBuilder[(SnowflakeType[K], V), SnowflakeMap[K, V]](empty) {
      override def addOne(elem: (SnowflakeType[K], V)): this.type = {
        elems = elems + elem
        this
      }
    }

  private def keyToSnowflake(k: Long): Key = SnowflakeType[K](k)

  override def empty: SnowflakeMap[K, V] = new SnowflakeMap(inner.empty)

  override def toList: List[(Key, V)] = {
    val buffer = new ListBuffer[(Key, V)]
    foreach(buffer += _)
    buffer.toList
  }

  override def iterator: Iterator[(Key, V)] = inner.iterator.map { case (k, v) => (keyToSnowflake(k), v) }

  final override def foreach[U](f: ((Key, V)) => U): Unit =
    inner.foreach { case (k, v) => f((keyToSnowflake(k), v)) }

  final override def foreachEntry[U](f: (Key, V) => U): Unit =
    inner.foreachEntry((k, v) => f(keyToSnowflake(k), v))

  override def keysIterator: Iterator[Key] = inner.keysIterator.map(keyToSnowflake)

  /**
    * Loop over the keys of the map. The same as keys.foreach(f), but may be
    * more efficient.
    *
    * @param f
    *   The loop body
    */
  final def foreachKey(f: Key => Unit): Unit = inner.foreachKey(k => f(keyToSnowflake(k)))

  override def valuesIterator: Iterator[V] = inner.valuesIterator

  /**
    * Loop over the values of the map. The same as values.foreach(f), but may be
    * more efficient.
    *
    * @param f
    *   The loop body
    */
  final def foreachValue(f: V => Unit): Unit = inner.foreachValue(f)

  override protected[this] def className = "SnowflakeMap"

  override def isEmpty: Boolean = inner.isEmpty

  override def knownSize: Int = inner.knownSize

  override def filter(p: ((Key, V)) => Boolean): SnowflakeMap[K, V] =
    new SnowflakeMap(inner.filter { case (k, v) => p((keyToSnowflake(k), v)) })

  override def transform[S](f: (Key, V) => S): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.transform[S] { case (k, v) => f(keyToSnowflake(k), v) })

  final override def size: Int = inner.size

  final override def get(key: Key): Option[V] = inner.get(key.toUnsignedLong)

  final override def getOrElse[V1 >: V](key: Key, default: => V1): V1 = inner.getOrElse(key.toUnsignedLong, default)

  final override def apply(key: Key): V = inner.apply(key.toUnsignedLong)

  override def +[V1 >: V](kv: (Key, V1)): SnowflakeMap[K, V1] =
    new SnowflakeMap(inner.updated(kv._1.toUnsignedLong, kv._2))

  override def updated[V1 >: V](key: Key, value: V1): SnowflakeMap[K, V1] =
    new SnowflakeMap(inner.updated(key.toUnsignedLong, value))

  /**
    * Updates the map, using the provided function to resolve conflicts if the
    * key is already present.
    *
    * Equivalent to
    * {{{
    *   this.get(key) match {
    *     case None => this.update(key, value)
    *     case Some(oldvalue) => this.update(key, f(oldvalue, value)
    *   }
    * }}}
    *
    * @tparam S
    *   The supertype of values in this `SnowflakeMap`.
    * @param key
    *   The key to update.
    * @param value
    *   The value to use if there is no conflict.
    * @param f
    *   The function used to resolve conflicts.
    * @return
    *   The updated map.
    */
  def updateWith[S >: V](key: Key, value: S, f: (V, S) => S): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.updateWith(key.toUnsignedLong, value, f))

  override def removed(key: Key): SnowflakeMap[K, V] = new SnowflakeMap(inner.removed(key.toUnsignedLong))

  /**
    * A combined transform and filter function. Returns an `SnowflakeMap` such
    * that for each `(key, value)` mapping in this map, if `f(key, value) ==
    * None` the map contains no mapping for key, and if `f(key, value)`.
    *
    * @tparam S
    *   The type of the values in the resulting `SnowflakeMap`.
    * @param f
    *   The transforming function.
    * @return
    *   The modified map.
    */
  def modifyOrRemove[S](f: (Key, V) => Option[S]): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.modifyOrRemove { case (k, v) => f(keyToSnowflake(k), v) })

  /**
    * Forms a union map with that map, using the combining function to resolve
    * conflicts.
    *
    * @tparam S
    *   The type of values in `that`, a supertype of values in `this`.
    * @param that
    *   The map to form a union with.
    * @param f
    *   The function used to resolve conflicts between two mappings.
    * @return
    *   Union of `this` and `that`, with identical key conflicts resolved using
    *   the function `f`.
    */
  def unionWith[S >: V](that: SnowflakeMap[Key, S], f: (Key, S, S) => S): SnowflakeMap[K, S] =
    new SnowflakeMap(inner.unionWith[S](that.inner, (l, s1, s2) => f(keyToSnowflake(l), s1, s2)))

  /**
    * Forms the intersection of these two maps with a combining function. The
    * resulting map is a map that has only keys present in both maps and has
    * values produced from the original mappings by combining them with `f`.
    *
    * @tparam S
    *   The type of values in `that`.
    * @tparam R
    *   The type of values in the resulting `SnowflakeMap`.
    * @param that
    *   The map to intersect with.
    * @param f
    *   The combining function.
    * @return
    *   Intersection of `this` and `that`, with values for identical keys
    *   produced by function `f`.
    */
  def intersectionWith[S, R](that: SnowflakeMap[Key, S], f: (Key, V, S) => R): SnowflakeMap[K, R] =
    new SnowflakeMap(inner.intersectionWith[S, R](that.inner, (l, v, s) => f(keyToSnowflake(l), v, s)))

  /**
    * Left biased intersection. Returns the map that has all the same mappings
    * as this but only for keys which are present in the other map.
    *
    * @tparam R
    *   The type of values in `that`.
    * @param that
    *   The map to intersect with.
    * @return
    *   A map with all the keys both in `this` and `that`, mapped to
    *   corresponding values from `this`.
    */
  def intersection[R](that: SnowflakeMap[K, R]): SnowflakeMap[K, V] = new SnowflakeMap(inner.intersection(that.inner))

  def ++[S >: V](that: SnowflakeMap[K, S]): SnowflakeMap[K, S] = new SnowflakeMap(inner ++ that.inner)

  final def firstKey: Key = keyToSnowflake(inner.firstKey)

  final def lastKey: Key = keyToSnowflake(inner.lastKey)

  def map[K2, V2](f: ((Key, V)) => (SnowflakeType[K2], V2)): SnowflakeMap[K2, V2] =
    SnowflakeMap.from(new View.Map(coll, f))

  def flatMap[K2, V2](f: ((Key, V)) => IterableOnce[(SnowflakeType[K2], V2)]): SnowflakeMap[K2, V2] =
    SnowflakeMap.from(new View.FlatMap(coll, f))

  override def concat[V1 >: V](that: collection.IterableOnce[(Key, V1)]): SnowflakeMap[K, V1] =
    super.concat(that).asInstanceOf[SnowflakeMap[K, V1]] // Already has corect type but not declared as such

  override def ++[V1 >: V](that: collection.IterableOnce[(Key, V1)]): SnowflakeMap[K, V1] = concat(that)

  def collect[K2, V2](pf: PartialFunction[(Key, V), (SnowflakeType[K2], V2)]): SnowflakeMap[K2, V2] =
    strictOptimizedCollect(SnowflakeMap.newBuilder[K2, V2], pf)

  protected[this] def writeReplace(): AnyRef =
    new DefaultSerializationProxy(SnowflakeMap.toFactory[K, V](SnowflakeMap), this)
}
object SnowflakeMap {

  /** Create an empty snowflake map. */
  def empty[K, V]: SnowflakeMap[K, V] = new SnowflakeMap(LongMap.empty)

  /** Create a snowflake map with a single value. */
  def singleton[K, V](key: SnowflakeType[K], value: V): SnowflakeMap[K, V] =
    new SnowflakeMap(LongMap.singleton(key.toUnsignedLong, value))

  /** Create a snowflake map from multiple values. */
  def apply[K, V](elems: (SnowflakeType[K], V)*): SnowflakeMap[K, V] =
    new SnowflakeMap(LongMap.apply(elems.map(t => t._1.toUnsignedLong -> t._2): _*))

  /** Create a snowflake map from an IterableOnce of snowflakes and values. */
  def from[K, V](coll: IterableOnce[(SnowflakeType[K], V)]): SnowflakeMap[K, V] =
    newBuilder[K, V].addAll(coll).result()

  def newBuilder[K, V]: mutable.Builder[(SnowflakeType[K], V), SnowflakeMap[K, V]] =
    new mutable.ImmutableBuilder[(SnowflakeType[K], V), SnowflakeMap[K, V]](empty) {
      override def addOne(elem: (SnowflakeType[K], V)): this.type = {
        elems = elems + elem
        this
      }
    }

  /**
    * Create a snowflake map from an iterable of values while using a provided
    * function to get the key.
    */
  def withKey[K, V](iterable: Iterable[V])(f: V => SnowflakeType[K]): SnowflakeMap[K, V] =
    from(iterable.map(v => f(v) -> v))

  implicit def toFactory[K, V](dummy: SnowflakeMap.type): Factory[(SnowflakeType[K], V), SnowflakeMap[K, V]] =
    ToFactory.asInstanceOf[Factory[(SnowflakeType[K], V), SnowflakeMap[K, V]]]

  @SerialVersionUID(3L)
  private[this] object ToFactory
      extends Factory[(SnowflakeType[AnyRef], AnyRef), SnowflakeMap[AnyRef, AnyRef]]
      with Serializable {
    def fromSpecific(it: IterableOnce[(SnowflakeType[AnyRef], AnyRef)]): SnowflakeMap[AnyRef, AnyRef] =
      SnowflakeMap.from[AnyRef, AnyRef](it)
    def newBuilder: mutable.Builder[(SnowflakeType[AnyRef], AnyRef), SnowflakeMap[AnyRef, AnyRef]] =
      SnowflakeMap.newBuilder[AnyRef, AnyRef]
  }

  implicit def toBuildFrom[K, V](
      factory: SnowflakeMap.type
  ): BuildFrom[Any, (SnowflakeType[K], V), SnowflakeMap[K, V]] =
    ToBuildFrom.asInstanceOf[BuildFrom[Any, (SnowflakeType[K], V), SnowflakeMap[K, V]]]
  private[this] object ToBuildFrom
      extends BuildFrom[Any, (SnowflakeType[AnyRef], AnyRef), SnowflakeMap[AnyRef, AnyRef]] {
    def fromSpecific(from: Any)(it: IterableOnce[(SnowflakeType[AnyRef], AnyRef)]): SnowflakeMap[AnyRef, AnyRef] =
      SnowflakeMap.from(it)
    def newBuilder(from: Any): mutable.Builder[(SnowflakeType[AnyRef], AnyRef), SnowflakeMap[AnyRef, AnyRef]] =
      SnowflakeMap.newBuilder[AnyRef, AnyRef]
  }

  implicit def iterableFactory[K, V]: Factory[(SnowflakeType[K], V), SnowflakeMap[K, V]] = toFactory(this)
  implicit def buildFromSnowflakeMap[K, V]: BuildFrom[SnowflakeMap[_, _], (SnowflakeType[K], V), SnowflakeMap[K, V]] =
    toBuildFrom(this)
}
