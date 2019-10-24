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
package ackcord.cachehandlers

import org.slf4j.Logger

/**
  * A class that handles creating a new cache snapshot with an object
  */
sealed trait CacheHandler[-Obj] {

  /**
    * Updates the builder with the object
    * @param builder The builder to update
    * @param obj The logger to update with
    * @param log A logging adapter
    */
  def handle(builder: CacheSnapshotBuilder, obj: Obj, registry: CacheTypeRegistry)(implicit log: Logger): Unit
}

/**
  * A [[CacheHandler]] for deletions.
  */
trait CacheDeleter[-Obj] extends CacheHandler[Obj]
object CacheDeleter {
  def dummy[Obj]: CacheDeleter[Obj] = new CacheDeleter[Obj] {
    override def handle(builder: CacheSnapshotBuilder, obj: Obj, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = ()
  }
}

/**
  * A [[CacheHandler]] for updates.
  */
trait CacheUpdater[-Obj] extends CacheHandler[Obj]
object CacheUpdater {
  def dummy[Obj]: CacheUpdater[Obj] = new CacheUpdater[Obj] {
    override def handle(builder: CacheSnapshotBuilder, obj: Obj, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = ()
  }
}

/**
  * A handler that takes no action
  */
object NOOPHandler extends CacheHandler[Any] {
  override def handle(builder: CacheSnapshotBuilder, obj: Any, registry: CacheTypeRegistry)(
      implicit log: Logger
  ): Unit = ()
}
