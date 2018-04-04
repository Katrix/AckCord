package net.katsstuff.ackcord.http.rest

import akka.NotUsed
import io.circe.Decoder
import net.katsstuff.ackcord.CacheSnapshotLike
import net.katsstuff.ackcord.data.DiscordProtocol._
import net.katsstuff.ackcord.data.{AuditLog, GuildId, Permission}
import net.katsstuff.ackcord.http.Routes
import net.katsstuff.ackcord.http.requests.RequestRoute

//Place for future audit log requests if they should ever appear

/**
  * Get an audit log for a given guild.
  */
case class GetGuildAuditLog[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
  extends NoParamsNiceResponseRequest[AuditLog, Ctx] {
  override def route: RequestRoute = Routes.getGuildAuditLogs(guildId)

  override def responseDecoder: Decoder[AuditLog] = Decoder[AuditLog]

  override def requiredPermissions: Permission = Permission.ViewAuditLog
  override def hasPermissions(implicit c: CacheSnapshotLike): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}
