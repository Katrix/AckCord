---
layout: docs
title: Shared command concepts
---

# {{page.title}}
Many things are shared between both the low and high and command systems in AckCord. Let's go over them here.

## Global shared concepts
Let's first go over the concepts on how commands are parsed, and everything else that happens before it's decided what command to run. These are things you define a few times in your bot.

### How are commands parsed?
Commands in AckCord relies on the idea of most commands looking a certain way, that is `[mention] <prefix><command> [args...]`, where mention is a setting you toggle when using commands. What AckCord then does is to listen to all created messages, and convert the ones that look like commands into raw command objects.

### CommandSettings: Prefixes and should mention
When you first construct your bot or commands object, you pass it a lot of settings. In these are the command settings. In the `CommandSettings` object you can set if a mention is needed to use commands, and which prefixes AckCord should look for when parsing commands. Note that there is also `AbstractCommandSettings` if you need more control over which prefixes apply to which message, or you have other dynamic needs.

### Custom parsing via custom flows (low level)
If the customized parsing provided from `AbstractCommandSettings` isn't enough for you, you can also go directly to the source and create the `Commands` object yourself. If you want an example on how to do this, check out `CmdStreams.cmdStreams`.

## Individually shared concepts
Next let's go over the concepts on how the system decides if a given command should be run. These are things defined once per command.

### CmdRefiner
Just like the shared stuff had `AbstractCommandSettings`, the individual stuff has `CmdRefiner` which serves mostly the same purpose. It's primary role is to decide if a `Cmd` object corresponds the the command it is bound to. The simplest check here is to use command names, although you can also do your own stuff. If it's decided that a command should not be run, an error can optionally be returned instead.

#### CmdInfo
The default `CmdRefiner` implementation is `CmdInfo` which contains 3 parameters, the command prefix, a list of valid command aliases, and a list of checks to run before running the command itself.

##### CmdFilter
While the first and second parameters are obvious, let's talk about the third one. Often times you have a command that can only be used in a guild, or by those with a specific permission, or by non bots. For cases like those you can use a `CmdFilter`. The filter will stop the command before it arrives at your handler, and optionally send an error message instead.

### CmdDescription
Often times you also want some extra information together with your command that can be used for help commands and similar. That's where the `CmdDescription` comes in. It allows you to specify a pretty name, description, usage and more for your command.