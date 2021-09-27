---
layout: docs title: ActionBuilder deep dive (Advanced)
---

# ActionBuilder deep dive

You've probably used a bunch of action builders at this point. How do they work though, and how can you extend them.

## ActionFunction

Everything is build on `ActionFunction`. At it's core, an `ActionFunction` is a
function `def flow[A]: Flow[I[A], Either[Option[E], O[A]], NotUsed]`. It represents a step that can be taken before the
action is processed.

Let's go over that piece for piece. `I[A]` here is the input type to the function. For example `CommandMessage`. The `A`
here is the parsed type. Such a step can either succeed (`Right[O[A]]`), or fails (`Left[Option[E]]`). If it fails, it
might, but doesn't have to return an error (`E`). The `O[A]` here represents the output type of this step, and the input
type of the next one. For example, `I`
could be `CommandMessage` and `O` could be `GuildCommandMessage`.

## ActionTransformer

`ActionTransformer` is a simpler `ActionFunction` that always succeeds. If you have a `FunctionK`, you can easily lift
it into an `ActionTransformer`
using `ActionTransformer.fromFuncK`. There's not much else to say about it.

## ActionBuilder

`ActionBuilder` is an `ActionFunction` which is considered the final step of the chain of functions before it's handed
off to the user.

## Composing existing functions

You're completely free to compose the existing functions as you see fit. As `AckCord` doesn't know the output type you
want to create, most functions that create an `ActionFunction` takes a `A => FunctionK[I, O]` for the input type to the
output type. Here the `A` type represents new info extracted by the `ActionFunction`, while `I` is the input type,
and `O` is the output type.

Use might for example look like this:

```scala
val Command: CommandBuilder[UserCommandMessage, NotUsed] =
  baseCommandBuilder.andThen(CommandBuilder.nonBot { user =>
    λ[CommandMessage ~> UserCommandMessage](m => UserCommandMessage.Default(user, m))
  })
```
