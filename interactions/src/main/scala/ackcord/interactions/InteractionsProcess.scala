package ackcord.interactions

import ackcord.gateway.data.GatewayDispatchEvent
import ackcord.gateway.{Context, DispatchEventProcess}
import ackcord.interactions.data.InteractionRequests
import ackcord.requests.Requests
import cats.Monad
import cats.syntax.all._

trait InteractionsProcess[F[_]] extends DispatchEventProcess[F] {

  def respondToPing: Boolean

  override def F: Monad[F]

  def requests: Requests[F, Any]

  def processCommandInteraction(interaction: Interaction, context: Context): F[Option[HighInteractionResponse[F]]] =
    F.pure(None)

  def processComponentInteraction(interaction: Interaction, context: Context): F[Option[HighInteractionResponse[F]]] =
    F.pure(None)

  def processModalInteraction(interaction: Interaction, context: Context): F[Option[HighInteractionResponse[F]]] =
    F.pure(None)

  private def extractAsyncPart(response: HighInteractionResponse[F]): F[Unit] = {
    implicit val monad: Monad[F] = F
    response match {
      case HighInteractionResponse.Acknowledge(andThenDo)        => andThenDo.void
      case HighInteractionResponse.UpdateMessageLater(andThenDo) => andThenDo.void
      case HighInteractionResponse.UpdateMessage(_, andThenDo)   => andThenDo.void
      case HighInteractionResponse.ChannelMessage(_, andThenDo)  => andThenDo.void
      case _                                                     => F.unit
    }
  }

  override def onDispatchEvent(event: GatewayDispatchEvent, context: Context): F[Context] = event match {
    case ev: GatewayDispatchEvent.InteractionCreate =>
      implicit val monad: Monad[F] = F
      val interaction              = ev.retype(Interaction)
      val responseF: F[Option[HighInteractionResponse[F]]] = interaction.tpe match {
        case Interaction.InteractionType.PING if respondToPing => F.pure(Some(HighInteractionResponse.Pong()))
        case Interaction.InteractionType.APPLICATION_COMMAND |
            Interaction.InteractionType.APPLICATION_COMMAND_AUTOCOMPLETE =>
          processCommandInteraction(interaction, context)

        case Interaction.InteractionType.MESSAGE_COMPONENT =>
          processComponentInteraction(interaction, context)

        case Interaction.InteractionType.MODAL_SUBMIT =>
          processModalInteraction(interaction, context)

        case _ => F.pure(None) //Ignore unknown interaction type
      }

      responseF.flatMap { optResponse =>
        optResponse.fold(F.pure(context)) { response =>
          requests
            .runRequest(
              InteractionRequests.createInteractionResponse(
                interaction.id,
                interaction.token,
                response.toDataInteractionResponse
              )
            )
            .flatMap(_ => extractAsyncPart(response))
            .as(context)
        }
      }

    case _ => F.pure(context)
  }
}
object InteractionsProcess {
  abstract class Base[F[_]](implicit override val F: Monad[F])
      extends DispatchEventProcess.Base[F]
      with InteractionsProcess[F]
}
