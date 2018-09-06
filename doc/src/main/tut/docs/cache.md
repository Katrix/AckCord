---
layout: docs
title: The cache (Advanced)
---

# {{page.title}}

Let's look a bit more in-depth into how the cache in AckCord works. At its core, the cache is a sink, which takes cache updates, and a source which supplies these cache updates, together with the cache state after applying them. Both of these streams can be materialized as many times as needed. Currently the only update types are `APIMessageCacheUpdate` and `MiscCacheUpdate`. Use `MiscCacheUpdate` if you want to modify the cache in some way yourself.

## APIMessage
So, what is an `APIMessage` then? An `APIMessage` is a more straightforward type for working with cache updates, where the update type is `APIMessageCacheUpdate`.

## Publishing to the cache
You can also publish other changes to the cache. In most cases, you can use the utility methods on the cache object itself (`publishSingle`, `publishMany`)

## Publishing to the gateway
While most requests are made through routes and HTTP requests, there are some that are done through the gateway. The `Cache` object contains a `Sink` called `gatewayPublish` that you can connect to, to publish these requests. The valid requests to send to this `Sink` are `StatusUpdate`, `VoiceStateUpdate`, `VoiceServerUpdate` and `RequestGuildMembers`.