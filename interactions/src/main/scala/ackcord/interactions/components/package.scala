package ackcord.interactions

import ackcord.data.{SelectMenu, TextButton}

package object components {

  implicit class TextButtonOps(private val button: TextButton) extends AnyVal {

    /**
      * Create a button handler to be executed when a button is clicked.
      * @param makeHandler Function to make the handler.
      * @return The button.
      */
    def onClick[A <: ComponentInteraction](makeHandler: String => AutoButtonHandler[A]): TextButton =
      onClickBoth(makeHandler)._1

    /**
      * Create a button handler to be executed when a button is clicked.
      * @param makeHandler Function to make the handler.
      * @return The button and the created handler.
      */
    def onClickBoth[A <: ComponentInteraction](
        makeHandler: String => ButtonHandler[A]
    ): (TextButton, ButtonHandler[A]) =
      (button, makeHandler(button.identifier))
  }

  implicit class SelectMenuOps(private val menu: SelectMenu) extends AnyVal {

    /**
      * Create a menu handler to be executed when an option is selected.
      * @param makeHandler Function to make the handler.
      * @return The menu.
      */
    def onSelect[A <: MenuInteraction](makeHandler: String => AutoMenuHandler[A]): SelectMenu =
      onSelectBoth(makeHandler)._1

    /**
      * Create a menu handler to be executed when an option is selected.
      * @param makeHandler Function to make the handler.
      * @return The menu and the created handler.
      */
    def onSelectBoth[A <: MenuInteraction](
        makeHandler: String => MenuHandler[A]
    ): (SelectMenu, MenuHandler[A]) =
      (menu, makeHandler(menu.customId))
  }
}
