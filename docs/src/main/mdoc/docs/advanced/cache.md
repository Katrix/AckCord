---
layout: docs title: The`Events object (Advanced)
---

# The `Events` object

Let's look a bit more in-depth into how the `Events` object in AckCord works.

## The cache

The main thing stored in here is the cache. Not a snapshot of the cache, just the cache. At its core, the cache is a
sink, which takes cache updates, a source which supplies these cache updates, and a flow which applies the updates to
the current state. Both of these streams can be materialized as many times as needed. Currently the only update types
are `APIMessageCacheUpdate` and `MiscCacheUpdate`. Use `MiscCacheUpdate` if you want to modify the cache in some way
yourself.

### APIMessage

So, what is an `APIMessage` then? An `APIMessage` is a more straightforward type for working with cache updates, where
the update type is `APIMessageCacheUpdate`.

### Publishing to the cache

You can also publish other changes to the cache. In most cases, you can use the utility methods on the cache object
itself (`publishSingle`, `publishMany`)

## Gateway events

Something else that the events object also gives you access to is the raw events coming from the Discord gateway. These
are not processed in any way. This can be both a positive and negative. Postive as you will always receive the event,
even if something went wrong in creating the `APIMessage` object. Negative as you need to handle the raw version of the
objects coming from the gateway.

### Publishing to the gateway

While most requests are made through HTTP requests, there are some that are done through the gateway. The `Cache` object
contains a `Sink` called `sendGatewayPublish`
that you use, to publish these requests. The valid requests to send to this `Sink`
are `StatusUpdate`, `VoiceStateUpdate`, `VoiceServerUpdate` and `RequestGuildMembers`.