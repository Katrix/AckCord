package ackcord.example

import ackcord.Requests
import ackcord.commands.{CommandController, NamedDescribedCommand, UserCommandMessage}
import ackcord.data.{ActionRow, Button}
import ackcord.interactions.buttons._
import ackcord.interactions.{ButtonInteraction, InteractionResponse}
import ackcord.syntax._
import akka.NotUsed

class MyButtonCommands(requests: Requests) extends CommandController(requests) {

  def buttonCommand(name: String)(f: UserCommandMessage[NotUsed] => Seq[ActionRow]): NamedDescribedCommand[NotUsed] = {
    Command
      .named(Seq("!"), Seq(name), mustMention = false) //Simplest way to name a command
      .described(name, "Test buttons")
      .withRequest(m =>
        m.textChannel
          .sendMessage(s"Here are some buttons", components = f(m))
      )
  }

  val hiButton: NamedDescribedCommand[NotUsed] = buttonCommand("makeEditLinkGenerator") { implicit cmdExecution =>
    Seq(
      ActionRow.of(
        Button
          .text("EmbedMarkdown")
          .onClick(new AutoButtonHandler(_, requests) {
            override def handle(implicit interaction: ButtonInteraction): InteractionResponse =
              sendMessage("Hi")
          })
      )
    )
  }

  val commands = Seq(hiButton)
}
