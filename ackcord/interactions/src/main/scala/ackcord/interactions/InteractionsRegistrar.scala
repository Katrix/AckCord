package ackcord.interactions

import ackcord.data.{ApplicationId, GuildId, UndefOrSome, UndefOrUndefined}
import ackcord.interactions.data.{ApplicationCommand, ApplicationCommandRequests}
import ackcord.requests.Requests
import cats.Applicative
import cats.syntax.all._

object InteractionsRegistrar {

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
  def createGuildCommands[F[_]: Applicative](
      applicationId: ApplicationId,
      guildId: GuildId,
      requests: Requests[F, Any],
      replaceAll: Boolean,
      commands: CreatedApplicationCommand[F]*
  ): F[Seq[ApplicationCommand]] = {

    if (replaceAll) {
      requests.runRequest(
        ApplicationCommandRequests.bulkOverwriteGuildApplicationCommands(
          applicationId,
          guildId,
          commands.map(_.toApplicationCommand(applicationId, UndefOrSome(guildId)))
        )
      )
    } else {
      commands.toSeq.traverse { command =>
        requests.runRequest(
          ApplicationCommandRequests.createGuildApplicationCommand(
            applicationId,
            guildId,
            command
              .toApplicationCommand(applicationId, UndefOrSome(guildId))
              .retype(ApplicationCommandRequests.CreateGuildApplicationCommandBody)
          )
        )
      }
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
  def createGlobalCommands[F[_]: Applicative](
      applicationId: ApplicationId,
      requests: Requests[F, Any],
      replaceAll: Boolean,
      commands: CreatedApplicationCommand[F]*
  ): F[Seq[ApplicationCommand]] = {

    if (replaceAll) {
      requests.runRequest(
        ApplicationCommandRequests.bulkOverwriteGlobalApplicationCommands(
          applicationId,
          commands.map(_.toApplicationCommand(applicationId, UndefOrUndefined()))
        )
      )
    } else {
      commands.toSeq.traverse { command =>
        requests.runRequest(
          ApplicationCommandRequests.createGlobalApplicationCommand(
            applicationId,
            command
              .toApplicationCommand(applicationId, UndefOrUndefined())
              .retype(ApplicationCommandRequests.CreateGlobalApplicationCommandBody)
          )
        )
      }
    }
  }
}
