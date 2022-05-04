---
layout: docs
title: Interactions
---

# {{page.title}}
Interactions are the new way of adding functionality to your application.

To use components, you need to have `Requests` available in scope.

# Buttons
```scala mdoc:silent
// This import is needed for the .onClick() method
import ackcord.interactions.components._

// Create a button
SlashCommand.command("click", "Click me!!") { _ =>
    sendMessage(
        "Go on, click it!",
        components = Seq(
            ActionRow.of(
                Button
                    .text("Click  Me!", "clickme")
                    .onClick(.onClick(new AutoButtonHandler[ComponentInteraction](_, requests) {
                        def handle(implicit interaction: ComponentInteraction): InteractionResponse = {
                            sendMessage("You clicked me, you're a star!")
                        }
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
                    .onClick(.onClick(new AutoButtonHandler[ComponentInteraction](_, requests) {
                        def handle(implicit interaction: ComponentInteraction): InteractionResponse = {
                            sendMessage("You chose yes!")
                        }
                    }),
                Button
                    .text("No", "no")
                    .onClick(.onClick(new AutoButtonHandler[ComponentInteraction](_, requests) {
                        def handle(implicit interaction: ComponentInteraction): InteractionResponse = {
                            sendMessage("You chose no?")
                        }
                    }),
            )
        )
    )
}
```

## Selections
Selections allow you to choose from preset options.

```scala mdoc:silent
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
```