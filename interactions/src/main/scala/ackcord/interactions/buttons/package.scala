package ackcord.interactions

import ackcord.data.TextButton

package object buttons {

  implicit class TextButtonOps(private val button: TextButton) extends AnyVal {

    def onClick[A <: ButtonInteraction](makeHandler: String => ButtonHandler[A]): TextButton =
      onClickBoth(makeHandler)._1

    def onClickBoth[A <: ButtonInteraction](
        makeHandler: String => ButtonHandler[A]
    ): (TextButton, ButtonHandler[A]) =
      (button, makeHandler(button.identifier))
  }
}
