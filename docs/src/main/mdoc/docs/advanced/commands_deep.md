---
layout: docs
title: Commands deep dive (Advanced)
---

# Commands deep dive
This section assumes you have read the action builders deep dive. All of the 
stuff in there also applies here.

## PrefixParser
The simplest way to name a command is to simply call the `named` command on it.
You can however also delay that until registration. At that point the easy way 
to get a name is to call `CommandConnector#prefix`, which returns a `PrefixParser`.
You are also free to construct a `PrefixParser` yourself. At it's core it's 
simply a function `(CacheSnapshot, Message) => Future[MessageParser[Unit]]`.
The function will be evaluated and tried for each incoming message. If the 
message parser succeeds, the remaining string will be used for the command itself.
If it fails, it will discard the message.

## Custom error handling
The default behavior when a command fails is to print an error message. If you 
want to instead do something else, call `CommandConnector#newCommandWithErrors` 
and friends. These will give your a source of command errors, that you can then 
handle however you want.
