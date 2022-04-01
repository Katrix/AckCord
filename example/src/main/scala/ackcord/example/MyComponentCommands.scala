package ackcord.example

import ackcord.Requests
import ackcord.commands.{CommandController, NamedDescribedCommand, UserCommandMessage}
import ackcord.data._
import ackcord.interactions.components._
import ackcord.interactions.{ComponentInteraction, InteractionResponse, MenuInteraction}
import ackcord.syntax._
import akka.NotUsed

class MyComponentCommands(requests: Requests) extends CommandController(requests) {

  def componentCommand(
      name: String
  )(f: UserCommandMessage[NotUsed] => Seq[ActionRow]): NamedDescribedCommand[NotUsed] = {
    Command
      .named(Seq("!"), Seq(name), mustMention = false) //Simplest way to name a command
      .described(name, "Test buttons")
      .withRequest(m =>
        m.textChannel
          .sendMessage(s"Here are some buttons", components = f(m))
      )
  }

  val hiButton: NamedDescribedCommand[NotUsed] = componentCommand("makeEditLinkGenerator") { _ =>
    Seq(
      ActionRow.of(
        Button
          .text("EmbedMarkdown")
          .onClick(new AutoButtonHandler(_, requests) {
            override def handle(implicit interaction: ComponentInteraction): InteractionResponse =
              sendMessage("Hi")
          })
      )
    )
  }

  val singleSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeSingleSelect") { _ =>
    Seq(
      ActionRow.of(
        StringSelect(
          Seq(
            SelectOption.of("Foo", Some("1. option")),
            SelectOption("Bar", "bar", Some("2. option")),
            SelectOption.of("baz")
          ),
          Some("Select something")
        ).onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val multiSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeMultiSelect") { _ =>
    Seq(
      ActionRow.of(
        StringSelect(
          Seq(
            SelectOption.of("Foo", Some("1. option")),
            SelectOption("Bar", "bar", Some("2. option")),
            SelectOption.of("baz")
          ),
          minValues = 0,
          maxValues = 3
        ).onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val singleRoleSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeSingleRoleSelect") { _ =>
    Seq(
      ActionRow.of(
        RoleSelect().onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse = {
            println(interaction.resolved)
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
          }
        })
      )
    )
  }

  val multiRoleSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeMultiRoleSelect") { _ =>
    Seq(
      ActionRow.of(
        RoleSelect(
          minValues = 0,
          maxValues = 3
        ).onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse = {
            println(interaction.resolved)
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
          }
        })
      )
    )
  }

  val singleChannelSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeSingleChannelSelect") { _ =>
    Seq(
      ActionRow.of(
        ChannelSelect().onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val multiChannelSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeMultiChannelSelect") { _ =>
    Seq(
      ActionRow.of(
        ChannelSelect(
          minValues = 0,
          maxValues = 3
        ).onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val singleUserSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeSingleUserSelect") { _ =>
    Seq(
      ActionRow.of(
        UserSelect().onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val multiUserSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeMultiUserSelect") { _ =>
    Seq(
      ActionRow.of(
        UserSelect(
          minValues = 0,
          maxValues = 3
        ).onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val singleMentionableSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeSingleMentionableSelect") { _ =>
    Seq(
      ActionRow.of(
        MentionableSelect().onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val multiMentionableSelect: NamedDescribedCommand[NotUsed] = componentCommand("makeMultiMentionableSelect") { _ =>
    Seq(
      ActionRow.of(
        MentionableSelect(
          minValues = 0,
          maxValues = 3
        ).onSelect(new AutoMenuHandler(_, requests) {
          override def handle(implicit interaction: MenuInteraction): InteractionResponse =
            sendMessage(s"Selected: ${interaction.values.mkString(", ")}")
        })
      )
    )
  }

  val commands = Seq(
    hiButton,
    singleSelect,
    multiSelect,
    singleRoleSelect,
    multiRoleSelect,
    singleChannelSelect,
    multiChannelSelect,
    singleUserSelect,
    multiUserSelect,
    singleMentionableSelect,
    multiMentionableSelect
  )
}
