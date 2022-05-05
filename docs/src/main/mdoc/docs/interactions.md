---
layout: docs
title: Interactions
---

# {{page.title}}
Interactions are the new way of adding functionality to your application.

To use components, you need to have `Requests` available in scope.

```scala mdoc:invisible
import ackcord._
import ackcord.data._
```

# Buttons
```scala mdoc:silent
import ackcord.interactions._
import ackcord.interactions.commands._
// This import is needed for the .onClick() method
import ackcord.interactions.components._

class MyCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  // Create a button
  SlashCommand.command("click", "Click me!!") { _ =>
    sendMessage(
      "Go on, click it!",
      components = Seq(
        ActionRow.of(
          Button
            .text("Click  Me!", "clickme")
            .onClick(new AutoButtonHandler[ComponentInteraction](_, requests) {
              def handle(implicit interaction: ComponentInteraction): InteractionResponse =
                sendMessage("You clicked me, you're a star!")
            })
        )
      )
    )
  }

  // Have 2 buttons
  SlashCommand.command("which", "Yes, or No?") { _ =>
    sendMessage(
      "Which will you choose...",
      components = Seq(
        ActionRow.of(
          Button
            .text("Yes", "yes")
            .onClick(new AutoButtonHandler[ComponentInteraction](_, requests) {
              def handle(implicit interaction: ComponentInteraction): InteractionResponse =
                sendMessage("You chose yes!")
            }),
          Button
            .text("No", "no")
            .onClick(new AutoButtonHandler[ComponentInteraction](_, requests) {
              def handle(implicit interaction: ComponentInteraction): InteractionResponse =
                sendMessage("You chose no?")
            })
        )
      )
    )
  }
}
```

## Selections
Selections allow you to choose from preset options.

```scala mdoc:silent
class MyOtherCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  SlashCommand.command("click", "Click me!!") { _ =>
    sendMessage(
      "Go on, click it!",
      components = Seq(
        ActionRow.of(
          SelectMenu(
            List(
              SelectOption("I'm Yes!", "yes"),
              SelectOption("I'm No!", "no")
            ),
            placeholder = Some("Are you sure?")
          )
        )
      )
    )
  }
}
```