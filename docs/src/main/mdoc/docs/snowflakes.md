---
layout: docs
title: Snowflake types
---

# {{page.title}}
Unlike other Discord libraries, AckCord doesn't have one specific snowflake type,
instead it uses a type `Snowflake[A]` to refer to something of type `A`.
Most of these types also have aliases, like `UserId` being an alias
for `Snowflake[User]`.

## Converting to snowflake
To convert a long or string(prefer string) to a snowflake, use the apply method
on the companion object. For example: `UserId(0L)`

You can also use this to "cast" one snowflake type to another.
For example: `RoleId(guildId)`.

## Raw snowflakes
In some cases it's not possible to give a concrete type to the snowflake.
In those cases `RawSnowflake` is used instead, an alias for `SnowflakeType[Any]`.
In most cases you'll have to cast this to what you need yourself.
