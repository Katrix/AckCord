package ackcord.example

import ackcord.Requests
import ackcord.commands.{
  CommandController,
  NamedDescribedCommand,
  UserCommandMessage
}
import ackcord.data.{ActionRow, Button, SelectMenu, SelectOption}
import ackcord.interactions.components._
import ackcord.interactions.{
  ComponentInteraction,
  InteractionResponse,
  MenuInteraction
}
import ackcord.syntax._
import akka.NotUsed

class MyComponentCommands(requests: Requests)
    extends CommandController(requests) {

  def componentCommand(
      name: String
  )(
      f: UserCommandMessage[NotUsed] => Seq[ActionRow]
  ): NamedDescribedCommand[NotUsed] = {
    Command
      .named(
        Seq("!"),
        Seq(name),
        mustMention = false
      ) //Simplest way to name a command
      .described(name, "Test buttons")
      .withRequest(m =>
        m.textChannel
          .sendMessage(s"Here are some buttons", components = f(m))
      )
  }

  val hiButton: NamedDescribedCommand[NotUsed] =
    componentCommand("makeEditLinkGenerator") { _ =>
      Seq(
        ActionRow.of(
          Button
            .text("EmbedMarkdown")
            .onClick(new AutoButtonHandler(_, requests) {
              override def handle(implicit
                  interaction: ComponentInteraction
              ): InteractionResponse =
                sendMessage("Hi")
            })
        )
      )
    }

  val singleSelect: NamedDescribedCommand[NotUsed] =
    componentCommand("makeSingleSelect") { _ =>
      Seq(
        ActionRow.of(
          SelectMenu(
            Seq(
              SelectOption.of("Foo", Some("1. option")),
              SelectOption("Bar", "bar", Some("2. option")),
              SelectOption.of("baz")
            ),
            Some("Select something")
          ).onSelect(new AutoMenuHandler(_, requests) {
            override def handle(implicit
                interaction: MenuInteraction
            ): InteractionResponse =
              sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
          })
        )
      )
    }

  val multiSelect: NamedDescribedCommand[NotUsed] =
    componentCommand("makeMultiSelect") { _ =>
      Seq(
        ActionRow.of(
          SelectMenu(
            Seq(
              SelectOption.of("Foo", Some("1. option")),
              SelectOption("Bar", "bar", Some("2. option")),
              SelectOption.of("baz")
            ),
            minValues = 0,
            maxValues = 3
          ).onSelect(new AutoMenuHandler(_, requests) {
            override def handle(implicit
                interaction: MenuInteraction
            ): InteractionResponse =
              sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
          })
        )
      )
    }

  val commands = Seq(hiButton, singleSelect, multiSelect)
}
