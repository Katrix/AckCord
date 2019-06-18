---
layout: docs
title: Sending custom sound (Advanced)
---

# {{page.title}}
First of, let me issue a warning. This page is for if you want to implement your own sound provider. If all you're interested in is sending music or similar, take a look at the lavaplayer module. The recommended way to use audio with AckCord at this point is through the lavaplayer module. However, if you want to create a custom implementation, read on.

## Manual labor
If you want to send sound with AckCord, you currently have to wire some stuff up yourself. AckCord still hides most of the implementations, but you are assumed to have some knowledge of how sound sending works in Discord. If you are not familiar with this, read the [Discord docs for voice connections](https://discordapp.com/developers/docs/topics/voice-connections).

## Establishing a connection
The first thing you need to do is to tell Discord that you want to begin speaking and sending audio. You do this by sending a `VoiceStateUpdate` message to the gateway. From there you wait for two events. `APIMessage.VoiceStateUpdate` which gives you access to your `VoiceState` and as such your `sessionId`, and `APIMessage.VoiceServerUpdate` which gives you access to the token and endpoint. Once you have those 3, you want to create and login a voice WebSocket connection. To do this, create a `VoiceWsHandler` actor, and send it the `Login` message. When creating the actor, you have to decide which actor will receive the audio API messages. You can also specify another actor who will be the actor that receives the audio (I would recommend a dedicated actor with a pinned dispatcher(akka.io.pinned-dispatcher) for the actor that receives the audio). Lastly you need a source of voice data

## Sending the data
Once you have the WS actor set up, you should expect an `AudioAPIMessage.Ready` message sent to the actor which you specified should receive all the audio API messages. At this point you can tell your sound producer (how you do this is up to your) that it should begin to send data. Try to keep the rate here around 20ms per packet. Before sending any data, you always have to send a `SetSpeaking(true)` message to the WS handler.

### Other things to keep in mind
If you follow the advice above, creating an audio sender should hopefully not be too hard. One other thing you need to remember is that whenever you stop speaking, you need to send five packets of silence.