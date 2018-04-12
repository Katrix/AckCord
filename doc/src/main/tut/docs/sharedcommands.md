---
layout: docs
title: Shared command concepts
---

# {{page.title}}
Many things are shared between both the low and high and command systems in AckCord. Let's go over them here.

## How are commands parsed?

Commands in AckCord relies on the idea of most commands looking a certain way, that is `[mention] <prefix><command> [args...]`, where mention is a setting you toggle when using commands. What AckCord then does is to listen to all created messages, and convert the ones that look like commands into raw command objects.

## CmdCategory

When you first construct your bot or commands object, you pass it a lot of settings. In these are the command settings. The important part I want to focus on here are the categories. The categories you pass in tells AckCord what it should watch out for when parsing commands. Categories are what decides which prefixes are valid. They also come with a description which you can use as you please.

## CmdFilter
Often times you have a command that can only be used in a guild, or by those with a specific permission, or by non bots. For cases like those you can use a `CmdFilter`. The filter will stop the command before it arrives at your handler, and send an error message instead.