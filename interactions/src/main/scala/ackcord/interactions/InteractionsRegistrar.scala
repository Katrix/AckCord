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

import java.util.Locale

import scala.concurrent.{ExecutionContext, Future}

import ackcord.data._
import ackcord.interactions.commands.CreatedApplicationCommand
import ackcord.interactions.components.{GlobalRegisteredComponents, RegisteredComponents}
import ackcord.interactions.raw._
import ackcord.requests.{Requests, SupervisionStreams}
import ackcord.{CacheSnapshot, OptFuture}
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.syntax.all._

object InteractionsRegistrar {

  private def handleInteraction(
      clientId: String,
      commandsByName: Map[String, Seq[CreatedApplicationCommand]],
      registeredComponents: RegisteredComponents,
      interaction: RawInteraction,
      optCache: Option[CacheSnapshot]
  ) =
    interaction.tpe match {
      case InteractionType.Ping => Right(InteractionResponse.Pong)
      case InteractionType.ApplicationCommand | InteractionType.ApplicationCommandAutocomplete =>
        interaction.data match {
          case Some(data: ApplicationCommandInteractionData) =>
            commandsByName
              .get(data.name.toLowerCase(Locale.ROOT))
              .flatMap(_.collectFirst { case cmd if cmd.commandType == data.`type` => cmd })
              .map(_.handleRaw(clientId, interaction, optCache))
              .toRight(None)

          case _ => Left(Some("None or invalid data sent for command execution or autocomplete"))
        }
      case InteractionType.MessageComponent =>
        interaction.data match {
          case Some(data: ApplicationComponentInteractionData) =>
            registeredComponents
              .handlerForIdentifier(data.customId)
              .orElse(GlobalRegisteredComponents.handlerForIdentifier(data.customId))
              .flatMap(_.handleRaw(clientId, interaction, data.customId, optCache))
              .toRight(None)

          case _ => Left(Some("None or invalid data sent for component execution"))
        }

      case InteractionType.Unknown(i) => Left(Some(s"Unknown interaction type $i"))
    }

  private def extractAsyncPart(response: InteractionResponse)(implicit ec: ExecutionContext): () => OptFuture[Unit] =
    response match {
      case InteractionResponse.Acknowledge(andThenDo)        => () => andThenDo().map(_ => ())
      case InteractionResponse.UpdateMessageLater(andThenDo) => () => andThenDo().map(_ => ())
      case InteractionResponse.UpdateMessage(_, andThenDo)   => () => andThenDo().map(_ => ())
      case InteractionResponse.ChannelMessage(_, andThenDo)  => () => andThenDo().map(_ => ())
      case _                                                 => () => OptFuture.unit
    }

  /*
  def webFlow(
      commands: CreatedApplicationCommand*
  )(
      clientId: String,
      publicKey: String,
      registeredComponents: RegisteredComponents = GlobalRegisteredComponents,
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
        .map { case (body, optTimestamp, optSignature) =>
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
        .map(_.map(handleInteraction(clientId, commandsByName, registeredComponents, _, None)))
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
   */

  /**
    * Wire up the gateway to interaction handling.
    * @param commands
    *   The application commands to handle.
    * @param clientId
    *   The client id.
    * @param requests
    *   An requests instance.
    * @param registeredButtons
    *   Where to check for component interaction handlers. Defaults to the
    *   global one.
    */
  def gatewayInteractions(
      commands: CreatedApplicationCommand*
  )(
      clientId: String,
      requests: Requests,
      registeredButtons: RegisteredComponents = GlobalRegisteredComponents,
      parallelism: Int = 4
  ): Sink[(RawInteraction, Option[CacheSnapshot]), NotUsed] = {
    import requests.system
    import requests.system.executionContext
    val commandsByName = commands.groupBy(_.name.toLowerCase(Locale.ROOT))

    SupervisionStreams.logAndContinue(
      Flow[(RawInteraction, Option[CacheSnapshot])]
        .mapConcat { case (interaction, optCache) =>
          handleInteraction(clientId, commandsByName, registeredButtons, interaction, optCache)
            .map(_ -> interaction)
            .toList
        }
        .map { case (response, interaction) =>
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

  /**
    * Create or overwrite guild application commands.
    * @param applicationId
    *   The id of the application to handle the commands for.
    * @param guildId
    *   The guild to add or replace the guild application commands for.
    * @param requests
    *   An requests instance.
    * @param replaceAll
    *   If all existing commands should be removed in favor of these new ones.
    * @param commands
    *   The commands to add or replace.
    * @return
    *   The created commands.
    */
  def createGuildCommands(
      applicationId: ApplicationId,
      guildId: GuildId,
      requests: Requests,
      replaceAll: Boolean,
      commands: CreatedApplicationCommand*
  ): Future[Seq[ApplicationCommand]] = {
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    if (replaceAll) {
      requests.singleFutureSuccess(
        BulkOverwriteGuildApplicationCommands(applicationId, guildId, commands.map(CreateCommandData.fromCommand))
      )
    } else {
      requests.manyFutureSuccess(
        commands.toVector.map(command =>
          CreateGuildApplicationCommand(applicationId, guildId, CreateCommandData.fromCommand(command))
        )
      )
    }
  }

  /**
    * Create or overwrite global application commands.
    * @param applicationId
    *   The id of the application to handle the commands for.
    * @param requests
    *   An requests instance.
    * @param replaceAll
    *   If all existing commands should be removed in favor of these new ones.
    * @param commands
    *   The commands to add or replace.
    * @return
    *   The created commands.
    */
  def createGlobalCommands(
      applicationId: ApplicationId,
      requests: Requests,
      replaceAll: Boolean,
      commands: CreatedApplicationCommand*
  ): Future[Seq[ApplicationCommand]] = {
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    if (replaceAll) {
      requests.singleFutureSuccess(
        BulkOverwriteGlobalApplicationCommands(applicationId, commands.map(CreateCommandData.fromCommand))
      )
    } else {
      requests.manyFutureSuccess(
        commands.toVector.map(command =>
          CreateGlobalApplicationCommand(applicationId, CreateCommandData.fromCommand(command))
        )
      )
    }
  }

  /**
    * Given a bunch of application commands, removes registered guild
    * application commands with names not matching the names found in the passed
    * in commands.
    *
    * @param applicationId
    *   The id of the application to handle the commands for.
    * @param guildId
    *   The guild to remove the guild application commands from.
    * @param requests
    *   An requests instance.
    * @param commands
    *   The commands to validate against.
    * @return
    *   The removed commands.
    */
  def removeUnknownGuildCommands(
      applicationId: ApplicationId,
      guildId: GuildId,
      requests: Requests,
      commands: CreatedApplicationCommand*
  ): Future[Seq[ApplicationCommand]] = {
    import requests.system
    import requests.system.executionContext
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    requests.singleFutureSuccess(GetGlobalApplicationCommands(applicationId)).flatMap { globalCommands =>
      Source(
        globalCommands
          .filter(gc => commands.exists(_.name.equalsIgnoreCase(gc.name)))
          .map(gc => (DeleteGuildApplicationCommand(applicationId, guildId, gc.id), gc))
          .toVector
      ).via(requests.flowSuccess(ignoreFailures = false).asFlow.map(_._2)).runWith(Sink.seq)
    }
  }

  /**
    * Given a bunch of application commands, removes registered global
    * application commands with names not matching the names found in the passed
    * in commands.
    *
    * @param applicationId
    *   The id of the application to handle the commands for.
    * @param requests
    *   An requests instance.
    * @param commands
    *   The commands to validate against.
    * @return
    *   The removed commands.
    */
  def removeUnknownGlobalCommands(
      applicationId: ApplicationId,
      requests: Requests,
      commands: CreatedApplicationCommand*
  ): Future[Seq[ApplicationCommand]] = {
    import requests.system
    import requests.system.executionContext
    //Ordered as this will likely be called once with too many requests
    implicit val requestProperties: Requests.RequestProperties = Requests.RequestProperties.ordered

    requests.singleFutureSuccess(GetGlobalApplicationCommands(applicationId)).flatMap { globalCommands =>
      Source(
        globalCommands
          .filter(gc => commands.exists(_.name.equalsIgnoreCase(gc.name)))
          .map(gc => (DeleteGlobalApplicationCommand(applicationId, gc.id), gc))
          .toVector
      ).via(requests.flowSuccess(ignoreFailures = false).asFlow.map(_._2)).runWith(Sink.seq)
    }
  }
}
