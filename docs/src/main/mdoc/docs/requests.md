---
layout: docs
title: Requests
---

# {{page.title}}

Requests are how most bot actions are done. Stuff like sending messages,
creating channels and managing users. AckCord represents every request as an
object you construct. To run these requests, you need a `Requests` object, which 
is backed by sttp. The easiest way to get a `Requests` is using the one found 
through `BotSettings#requests`. The get the request object, you call a function 
to create it on an object defined in `ackcord.requests`. Which object it is in 
depends on what page in the discord docs the request appears on.
