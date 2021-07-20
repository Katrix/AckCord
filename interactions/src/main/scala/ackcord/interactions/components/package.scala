package ackcord.interactions

import ackcord.data.{SelectMenu, TextButton}

package object components {

  implicit class TextButtonOps(private val button: TextButton) extends AnyVal {

    def onClick[A <: ComponentInteraction](makeHandler: String => ButtonHandler[A]): TextButton =
      onClickBoth(makeHandler)._1

    def onClickBoth[A <: ComponentInteraction](
        makeHandler: String => ButtonHandler[A]
    ): (TextButton, ButtonHandler[A]) =
      (button, makeHandler(button.identifier))
  }

  implicit class SelectMenuOps(private val menu: SelectMenu) extends AnyVal {

    def onSelect[A <: MenuInteraction](makeHandler: String => MenuHandler[A]): SelectMenu =
      onSelectBoth(makeHandler)._1

    def onSelectBoth[A <: MenuInteraction](
        makeHandler: String => MenuHandler[A]
    ): (SelectMenu, MenuHandler[A]) =
      (menu, makeHandler(menu.customId))
  }
}
