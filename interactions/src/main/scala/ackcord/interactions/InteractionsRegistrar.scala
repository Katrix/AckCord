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
package ackcord.interactions

import java.nio.charset.StandardCharsets
import java.util.Locale

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.requests.{Requests, SupervisionStreams}
import ackcord.interactions.buttons.{GlobalRegisteredButtons, RegisteredButtons}
import ackcord.interactions.commands.CommandOrGroup
import ackcord.interactions.raw._
import ackcord.{CacheSnapshot, OptFuture}
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import cats.syntax.all._
import com.iwebpp.crypto.TweetNaclFast
import io.circe.syntax._
import org.slf4j.LoggerFactory

object InteractionsRegistrar {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private def handleInteraction(
      clientId: String,
      commandsByName: Map[String, Seq[CommandOrGroup]],
      registeredButtons: RegisteredButtons,
      interaction: RawInteraction,
      optCache: Option[CacheSnapshot]
  ) =
    interaction.tpe match {
      case InteractionType.Ping => Right(InteractionResponse.Pong)
      case InteractionType.ApplicationCommand =>
        interaction.data match {
          case Some(data: ApplicationCommandInteractionData) =>
            commandsByName
              .get(data.name.toLowerCase(Locale.ROOT))
              .map(_.head.handleRaw(clientId, interaction, optCache))
              .toRight(None)

          case _ => Left(Some("None or invalid data sent for command execution"))
        }
      case InteractionType.ComponentClicked =>
        interaction.data match {
          case Some(data: ApplicationComponentInteractionData) =>
            registeredButtons
              .handlerForIdentifier(data.customId)
              .orElse(GlobalRegisteredButtons.handlerForIdentifier(data.customId))
              .map(_.handleRaw(clientId, interaction, optCache))
              .toRight(None)

          case _ => Left(Some("None or invalid data sent for component execution"))
        }

      case InteractionType.Unknown(i) => Left(Some(s"Unknown interaction type $i"))
    }

  def extractAsyncPart(response: InteractionResponse)(implicit ec: ExecutionContext): () => OptFuture[Unit] =
    response match {
      case InteractionResponse.Acknowledge(andThenDo)        => () => andThenDo().map(_ => ())
      case InteractionResponse.AcknowledgeLoading(andThenDo) => () => andThenDo().map(_ => ())
      case InteractionResponse.ChannelMessage(_, andThenDo)  => () => andThenDo().map(_ => ())
      case _                                                 => () => OptFuture.unit
    }

  def webFlow(
      commands: CommandOrGroup*
  )(
      clientId: String,
      publicKey: String,
      registeredButtons: RegisteredButtons = GlobalRegisteredButtons,
      parallelism: Int = 4
  )(
      implicit system: ActorSystem[Nothing]
  ): Flow[HttpRequest, (HttpResponse, () => OptFuture[Unit]), NotUsed] = {
    import system.executionContext
    val commandsByName = commands.groupBy(_.name.toLowerCase(Locale.ROOT))
    //https://stackoverflow.com/a/140861
    def hexStringToByteArray(s: String) = {
      val len  = s.length
      val data = new Array[Byte](len / 2)
      var i    = 0
      while (i < len) {
        data(i / 2) = ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16)).toByte

        i += 2
      }
      data
    }

    val signatureObj = new TweetNaclFast.Signature(hexStringToByteArray(publicKey), new Array(0))

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

          val timestamp = request.headers.find(_.lowercaseName == "X-Signature-Timestamp").map(_.value)
          val signature = request.headers.find(_.lowercaseName == "x-signature-ed25519").map(_.value)

          body.map((_, timestamp, signature))
        }
        .map {
          case (body, optTimestamp, optSignature) =>
            for {
              timestamp <-
                optTimestamp.toRight(HttpResponse(StatusCodes.Unauthorized, entity = "No timestamp in request"))
              signature <-
                optSignature.toRight(HttpResponse(StatusCodes.Unauthorized, entity = "No signature in request"))
              _ <- {
                val isValid =
                  signatureObj.detached_verify((timestamp + body).getBytes("UTF-8"), hexStringToByteArray(signature))
                Either.cond(isValid, (), HttpResponse(StatusCodes.Unauthorized, entity = "Invalid signature"))
              }
              res <- {
                io.circe.parser
                  .parse(body)
                  .flatMap(_.as[RawInteraction])
                  .leftMap { e =>
                    logger.error(s"Error when decoding command interaction: ${e.show}")
                    HttpResponse(StatusCodes.BadRequest, entity = e.show)
                  }
              }
            } yield res
        }
        .map(_.map(handleInteraction(clientId, commandsByName, registeredButtons, _, None)))
        .mapConcat {
          case Left(value)  => List(Left(value))
          case Right(value) => value.toList.map(Right.apply)
        }
        .map { e =>
          val httpResponse = e.map { response =>
            HttpResponse(
              headers = immutable.Seq(`Content-Type`(ContentType.WithFixedCharset(MediaTypes.`application/json`))),
              entity = response.toRawInteractionResponse.asJson.noSpaces
            )
          }.merge

          val action = e.fold(_ => () => OptFuture.unit, extractAsyncPart)

          (httpResponse, action)
        }
    )
  }

  def gatewayInteractions[Mat](
      commands: CommandOrGroup*
  )(
      clientId: String,
      requests: Requests,
      registeredButtons: RegisteredButtons = GlobalRegisteredButtons,
      parallelism: Int = 4
  ): Sink[(RawInteraction, Option[CacheSnapshot]), NotUsed] = {
    import requests.system
    import requests.system.executionContext
    val commandsByName = commands.groupBy(_.name.toLowerCase(Locale.ROOT))

    SupervisionStreams.logAndContinue(
      Flow[(RawInteraction, Option[CacheSnapshot])]
        .mapConcat {
          case (interaction, optCache) =>
            handleInteraction(clientId, commandsByName, registeredButtons, interaction, optCache)
              .map(_ -> interaction)
              .toList
        }
        .map {
          case (response, interaction) =>
            CreateInteractionResponse(
              interaction.id,
              interaction.token,
              response.toRawInteractionResponse
            ) -> extractAsyncPart(response)
        }
        .via(requests.flowSuccess(ignoreFailures = false))
        .map(_._2)
        .to(Sink.foreachAsync(parallelism)(action => action().value.map(_ => ())))
    )
  }

  def createGuildCommands(
      applicationId: ApplicationId,
      guildId: GuildId,
      requests: Requests,
      replaceAll: Boolean,
      commands: CommandOrGroup*
  ): Future[Seq[ApplicationCommand]] = {
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    if (replaceAll) {
      requests.singleFutureSuccess(
        BulkReplaceGuildCommand(applicationId, guildId, commands.map(CreateCommandData.fromCommand))
      )
    } else {
      requests.manyFutureSuccess(
        commands.toVector.map(command =>
          CreateGuildCommand(applicationId, guildId, CreateCommandData.fromCommand(command))
        )
      )
    }
  }

  def createGlobalCommands(
      applicationId: ApplicationId,
      requests: Requests,
      replaceAll: Boolean,
      commands: CommandOrGroup*
  ): Future[Seq[ApplicationCommand]] = {
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    if (replaceAll) {
      requests.singleFutureSuccess(
        BulkReplaceGlobalCommands(applicationId, commands.map(CreateCommandData.fromCommand))
      )
    } else {
      requests.manyFutureSuccess(
        commands.toVector.map(command => CreateGlobalCommand(applicationId, CreateCommandData.fromCommand(command)))
      )
    }
  }

  def removeUnknownGuildCommands(
      applicationId: ApplicationId,
      guildId: GuildId,
      requests: Requests,
      commands: CommandOrGroup*
  ): Future[Seq[ApplicationCommand]] = {
    import requests.system
    import requests.system.executionContext
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    requests.singleFutureSuccess(GetGlobalCommands(applicationId)).flatMap { globalCommands =>
      Source(
        globalCommands
          .filter(gc => commands.exists(_.name.equalsIgnoreCase(gc.name)))
          .map(gc => (DeleteGuildCommand(applicationId, guildId, gc.id), gc))
          .toVector
      ).via(requests.flowSuccess(ignoreFailures = false).asFlow.map(_._2)).runWith(Sink.seq)
    }
  }

  def removeUnknownGlobalCommands(
      applicationId: ApplicationId,
      requests: Requests,
      commands: CommandOrGroup*
  ): Future[Seq[ApplicationCommand]] = {
    import requests.system
    import requests.system.executionContext
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    requests.singleFutureSuccess(GetGlobalCommands(applicationId)).flatMap { globalCommands =>
      Source(
        globalCommands
          .filter(gc => commands.exists(_.name.equalsIgnoreCase(gc.name)))
          .map(gc => (DeleteGlobalCommand(applicationId, gc.id), gc))
          .toVector
      ).via(requests.flowSuccess(ignoreFailures = false).asFlow.map(_._2)).runWith(Sink.seq)
    }
  }
}
