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

import ackcord.data.{Guild, Presence}
import ackcord.gateway.GatewayEvent.PresenceUpdateData

object PresenceUpdater extends CacheUpdater[PresenceUpdateData] {
  override def handle(
      builder: CacheSnapshotBuilder,
      obj: PresenceUpdateData,
      registry: CacheTypeRegistry
  ): Unit = {
    val PresenceUpdateData(
      partialUser,
      guildId,
      status,
      rawActivities,
      clientStatus
    ) = obj

    registry.updateData(builder)(partialUser)

    for {
      guildHandler <- registry.getUpdater[Guild]
      oldGuild <- builder.guildMap.get(guildId)
    } {

      val presencesToUse = if (registry.hasUpdater[Presence]) {
        val newActivities = rawActivities.map(_.toActivity)

        val newPresence =
          Presence(partialUser.id, status, newActivities, clientStatus)
        oldGuild.presences.updated(partialUser.id, newPresence)
      } else {
        oldGuild.presences
      }

      val newGuild = oldGuild.copy(presences = presencesToUse)

      guildHandler.handle(builder, newGuild, registry)
    }
  }
}
