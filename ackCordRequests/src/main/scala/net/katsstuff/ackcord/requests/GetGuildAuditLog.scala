/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.requests

import scala.language.higherKinds
import akka.NotUsed
import cats.Monad
import io.circe.Decoder
import net.katsstuff.ackcord.CacheSnapshot
import net.katsstuff.ackcord.data.DiscordProtocol._
import net.katsstuff.ackcord.data.{AuditLog, GuildId, Permission}

//Place for future audit log requests if they should ever appear

/**
  * Get an audit log for a given guild.
  */
case class GetGuildAuditLog[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[AuditLog, Ctx] {
  override def route: RequestRoute = Routes.getGuildAuditLogs(guildId)

  override def responseDecoder: Decoder[AuditLog] = Decoder[AuditLog]

  override def requiredPermissions: Permission = Permission.ViewAuditLog
  override def hasPermissions[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}
