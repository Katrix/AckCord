---
layout: docs title: Sending custom sound (Advanced)
---

# Sending custom sound

First of, let me issue a warning. This page is for if you want to implement your own sound provider. If all you're
interested in is sending music or similar, take a look at the lavaplayer module. If you're instead interested in using
something other than Lavaplayer, read on.

With that out of the way, let's talk sound. If you want to send sound with AckCord, you currently have to wire some
stuff up yourself. AckCord still hides most of the implementations, but you are assumed to have some knowledge of how
sound sending works in Discord. If you are not familiar with this, read
the [Discord docs for voice connections](https://discord.com/developers/docs/topics/voice-connections).

## Establishing a connection

The first thing you need to do is to tell Discord that you want to begin speaking and sending audio. You do this by
sending a `VoiceStateUpdate` message to the gateway. From there you wait for two events. `APIMessage.VoiceStateUpdate`
which gives you access to your `VoiceState` and as such your `sessionId`, and
`APIMessage.VoiceServerUpdate` which gives you access to the token and endpoint. Once you have those 3, you want to
create and login a voice WebSocket connection.

To do this, create a `VoiceHandler` actor. When creating the actor, you have to decide which actor will receive the
audio API messages. Lastly you also need a source of the voice data which will be sent to Discord. AckCord does no
processing on the data. That's your responsibility.

## Sending the data

Once you have the WS actor set up, you should expect an `AudioAPIMessage.Ready`
message sent to the actor which you specified should receive all the audio API messages. At this point you should make
the source you passed to the `VoiceHandler` begin to produce sound. Before sending any data, you always have to send
a `SetSpeaking(true)` message to the voice handler.

### Other things to keep in mind

If you follow the advice above, creating an audio sender should hopefully not be too hard. One other thing you need to
remember is that whenever you stop speaking, you need to send five packets of silence.

If you need more help, take a look at AckCord's lavaplayer implementation.
