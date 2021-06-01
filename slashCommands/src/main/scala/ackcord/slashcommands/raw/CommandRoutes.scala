/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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
package ackcord.slashcommands.raw

import ackcord.data.{ApplicationId, GuildId}
import ackcord.requests.RequestRoute
import ackcord.requests.Routes._
import ackcord.slashcommands.{CommandId, InteractionId}
import akka.http.scaladsl.model.HttpMethods._

object CommandRoutes {

  val commandId = new MinorParameter[CommandId]("commandId", _.asString)
  val interactionId = new MinorParameter[InteractionId]("interactionId", _.asString)

  val callback: (InteractionId, String) => RequestRoute = upcast(
    base / "interactions" / interactionId / token / "callback" toRequest POST
  )

  //Commands
  val application: RouteFunction[ApplicationId]                = base / "applications" / applicationId
  val globalCommands: RouteFunction[ApplicationId]             = application / "commands"
  val globalCommand: RouteFunction[(ApplicationId, CommandId)] = globalCommands / commandId

  val getCommands: ApplicationId => RequestRoute                = upcast(globalCommands.toRequest(GET))
  val postCommand: ApplicationId => RequestRoute                = upcast(globalCommands.toRequest(POST))
  val putCommands: ApplicationId => RequestRoute                = upcast(globalCommands.toRequest(PUT))
  val getCommand: (ApplicationId, CommandId) => RequestRoute    = upcast(globalCommand.toRequest(GET))
  val patchCommand: (ApplicationId, CommandId) => RequestRoute  = upcast(globalCommand.toRequest(PATCH))
  val deleteCommand: (ApplicationId, CommandId) => RequestRoute = upcast(globalCommand.toRequest(DELETE))

  val guildCommands: RouteFunction[(ApplicationId, GuildId)]             = application / "guilds" / guildId / "commands"
  val guildCommand: RouteFunction[((ApplicationId, GuildId), CommandId)] = guildCommands / commandId

  val getGuildCommands: (ApplicationId, GuildId) => RequestRoute              = upcast(guildCommands.toRequest(GET))
  val postGuildCommand: (ApplicationId, GuildId) => RequestRoute              = upcast(guildCommands.toRequest(POST))
  val putGuildCommands: (ApplicationId, GuildId) => RequestRoute              = upcast(guildCommands.toRequest(PUT))
  val getGuildCommand: (ApplicationId, GuildId, CommandId) => RequestRoute    = upcast(guildCommand.toRequest(GET))
  val patchGuildCommand: (ApplicationId, GuildId, CommandId) => RequestRoute  = upcast(guildCommand.toRequest(PATCH))
  val deleteGuildCommand: (ApplicationId, GuildId, CommandId) => RequestRoute = upcast(guildCommand.toRequest(DELETE))
}
