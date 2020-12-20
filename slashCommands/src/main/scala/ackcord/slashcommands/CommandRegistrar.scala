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
package ackcord.slashcommands

import java.nio.charset.StandardCharsets
import java.util.Locale

import scala.concurrent.{ExecutionContext, Future}

import ackcord.data.{GuildId, RawSnowflake}
import ackcord.requests.{Requests, SupervisionStreams}
import ackcord.slashcommands.raw.CommandsProtocol._
import ackcord.slashcommands.raw._
import ackcord.{CacheSnapshot, OptFuture}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString
import akka.{Done, NotUsed}
import cats.syntax.all._
import io.circe.Decoder
import io.circe.syntax._
import org.slf4j.LoggerFactory

object CommandRegistrar {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private def handleCommand(
      clientId: String,
      commandsByName: Map[String, Seq[CommandOrGroup]],
      interaction: RawInteraction,
      optCache: Option[CacheSnapshot]
  ) =
    interaction.`type` match {
      case InteractionType.Ping => Right(CommandResponse.Pong)
      case InteractionType.ApplicationCommand =>
        for {
          data <- interaction.data.toRight("No data sent for command execution")
          commands <-
            commandsByName
              .get(data.name.toLowerCase(Locale.ROOT))
              .toRight(s"No command registered named ${data.name.toLowerCase(Locale.ROOT)}")
        } yield commands.head.handleRaw(clientId, interaction, optCache)
      case InteractionType.Unknown(i) => Left(s"Unknown interaction type $i")
    }

  def extractAsyncPart(response: CommandResponse)(implicit ec: ExecutionContext): () => OptFuture[Unit] =
    response match {
      case CommandResponse.Acknowledge(andThenDo)          => () => andThenDo().map(_ => ())
      case CommandResponse.ChannelMessage(_, _, andThenDo) => () => andThenDo().map(_ => ())
      case _                                               => () => OptFuture.unit
    }

  def webFlow(
      commands: CommandOrGroup*
  )(clientId: String, parallelism: Int = 4)(
      implicit system: ActorSystem[Nothing]
  ): Flow[HttpRequest, (HttpResponse, () => OptFuture[Unit]), NotUsed] = {
    import system.executionContext
    val commandsByName = commands.groupBy(_.name.toLowerCase(Locale.ROOT))

    SupervisionStreams.logAndContinue(
      Flow[HttpRequest]
        .mapAsyncUnordered(parallelism) { request =>
          val body =
            if (request.entity.isKnownEmpty) Future.successful("")
            else
              request.entity.dataBytes
                .runFold(ByteString.empty)(_ ++ _)
                .map(
                  _.decodeString(request.entity.contentType.charsetOption.fold(StandardCharsets.UTF_8)(_.nioCharset))
                )

          val signature = request.headers.find(_.lowercaseName == "x-signature-ed25519").map(_.value)

          body.map(_ -> signature)
        }
        .map {
          case (body, signature) =>
            //TODO: Check the signature ed25519

            io.circe.parser
              .parse(body)
              .flatMap(_.as[RawInteraction])
              .leftMap { e =>
                logger.error(s"Error when decoding command interaction: ${e.show}")
                HttpResponse(StatusCodes.BadRequest, entity = e.show)
              }
        }
        .map {
          case Right(interaction) =>
            handleCommand(clientId, commandsByName, interaction, None).leftMap { e =>
              logger.error(s"Error handling command: $e")
              HttpResponse(StatusCodes.BadRequest, entity = e)
            }

          case Left(e) => Left(e)
        }
        .map { e =>
          val httpResponse = e.map { response =>
            HttpResponse(
              headers = Seq(`Content-Type`(ContentType.WithFixedCharset(MediaTypes.`application/json`))),
              entity = response.toInteractionResponse.asJson.noSpaces
            )
          }.merge

          val action = e.fold(_ => () => OptFuture.unit, extractAsyncPart)

          (httpResponse, action)
        }
    )
  }

  def gatewayCommands[Mat](
      commands: CommandOrGroup*
  )(
      clientId: String,
      requests: Requests,
      parallelism: Int = 4
  ): Sink[(Decoder.Result[RawInteraction], Option[CacheSnapshot]), NotUsed] = {
    import requests.system
    import requests.system.executionContext
    val commandsByName = commands.groupBy(_.name.toLowerCase(Locale.ROOT))

    SupervisionStreams.logAndContinue(
      Flow[(Decoder.Result[RawInteraction], Option[CacheSnapshot])]
        .mapConcat {
          case (result, optCache) =>
            result match {
              case Right(value) => List(value -> optCache)
              case Left(e) =>
                logger.error(s"Error reparsing interaction: $e")
                Nil
            }
        }
        .map {
          case (interaction, optCache) =>
            handleCommand(clientId, commandsByName, interaction, optCache).map(_ -> interaction)
        }
        .mapConcat {
          case Right((response, interaction)) =>
            List(
              InteractionCallback(
                interaction.id,
                interaction.token,
                response.toInteractionResponse
              ) -> extractAsyncPart(response)
            )
          case Left(e) =>
            logger.error(s"Error handling command: $e")
            Nil
        }
        .via(requests.flowSuccess(ignoreFailures = false))
        .map(_._2)
        .to(Sink.foreachAsync(parallelism)(action => action().value.map(_ => ())))
    )
  }

  def createGuildCommands(
      applicationId: RawSnowflake,
      guildId: GuildId,
      requests: Requests,
      commands: CommandOrGroup*
  ): Future[Seq[ApplicationCommand]] =
    requests.manyFutureSuccess(
      commands.map(command => CreateGuildCommand(applicationId, guildId, CreateCommandData.fromCommand(command)))
    )
}
