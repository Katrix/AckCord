package ackcord.interactions

import ackcord.data.{UndefOrSome, UndefOrUndefined}
import ackcord.interactions.data.InteractionResponse

sealed trait HighInteractionResponse[F[_]] {
  def toDataInteractionResponse: InteractionResponse = this match {
    case HighInteractionResponse.Pong() =>
      InteractionResponse.make20(InteractionResponse.InteractionCallbackType.PONG, UndefOrUndefined())
    case HighInteractionResponse.Acknowledge(_) =>
      InteractionResponse.make20(
        InteractionResponse.InteractionCallbackType.DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE,
        UndefOrUndefined()
      )
    case HighInteractionResponse.UpdateMessageLater(_) =>
      InteractionResponse.make20(
        InteractionResponse.InteractionCallbackType.DEFERRED_UPDATE_MESSAGE,
        UndefOrUndefined()
      )
    case HighInteractionResponse.UpdateMessage(data, _) =>
      InteractionResponse.make20(InteractionResponse.InteractionCallbackType.UPDATE_MESSAGE, UndefOrSome(data))
    case HighInteractionResponse.ChannelMessage(message, _) =>
      InteractionResponse.make20(
        InteractionResponse.InteractionCallbackType.CHANNEL_MESSAGE_WITH_SOURCE,
        UndefOrSome(message)
      )
    case HighInteractionResponse.Autocomplete(choices) =>
      InteractionResponse.make20(
        InteractionResponse.InteractionCallbackType.APPLICATION_COMMAND_AUTOCOMPLETE_RESULT,
        UndefOrSome(InteractionResponse.AutocompleteData.make20(choices))
      )
    case HighInteractionResponse.Modal(modal, _) =>
      InteractionResponse.make20(
        InteractionResponse.InteractionCallbackType.MODAL,
        UndefOrSome(modal)
      )
  }
}
object HighInteractionResponse {
  sealed trait AsyncMessageable[F[_]] extends HighInteractionResponse[F] {

    /** Do something extra async after sending the response. */
    def doAsync[A](invocation: InteractionInvocation[_])(action: AsyncMessageToken => F[A]): HighInteractionResponse[F]
  }

  case class Pong[F[_]]()                                 extends HighInteractionResponse[F]
  case class Acknowledge[F[_], A](andThenDo: F[A])        extends HighInteractionResponse[F]
  case class UpdateMessageLater[F[_], A](andThenDo: F[A]) extends HighInteractionResponse[F]
  case class UpdateMessage[F[_], A](
      message: InteractionResponse.MessageData,
      andThenDo: F[A]
  ) extends HighInteractionResponse[F]
      with AsyncMessageable[F] {

    override def doAsync[B](invocation: InteractionInvocation[_])(
        action: AsyncMessageToken => F[B]
    ): HighInteractionResponse[F] =
      copy(andThenDo = action(AsyncToken.fromInteractionWithMessage(invocation.interaction)))
  }
  case class ChannelMessage[F[_], A](
      message: InteractionResponse.MessageData,
      andThenDo: F[A]
  ) extends HighInteractionResponse[F]
      with AsyncMessageable[F] {

    def doAsync[B](invocation: InteractionInvocation[_])(action: AsyncMessageToken => F[B]): ChannelMessage[F, B] =
      copy(andThenDo = action(AsyncToken.fromInteractionWithMessage(invocation.interaction)))
  }
  case class Modal[F[_], A](
      modal: InteractionResponse.ModalData,
      andThenDo: F[A]
  ) extends HighInteractionResponse[F]

  private[interactions] case class Autocomplete[F[_]](
      choices: Seq[data.ApplicationCommand.ApplicationCommandOption.ApplicationCommandOptionChoice]
  ) extends HighInteractionResponse[F]
}
