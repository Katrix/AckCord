---
layout: docs
title: Sending custom sound (Advanced)
---

# {{page.title}}
First of, let me issue a warning. This page is for if you want to implement your own sound provider. If all you're interested in is sending music or similar, take a look at the lavaplayer module. The recommended way to use audio with AckCord at this point is through the lavaplayer module. However, if you want to create a custom implementation, read on.

## Manual labor
If you want to send sound with AckCord, you currently have to set up and wire up most of the actors yourself. AckCord still hides the details of the WebSocket and UDP connection though. AckCord assumes that you are familiar with how sound works in Discord. If you are not familiar with this, read the [Discord docs for voice connections](https://discordapp.com/developers/docs/topics/voice-connections)

## Establishing a connection
The first thing you need to do is to tell Discord that you want to begin speaking and send audio. You do this by sending a `VoiceStateUpdate` message to the client (and as such to the gateway). From there you wait for two events. `APIMessage.VoiceStateUpdate` which gives you access to your `VoiceState` and as such your `sessionId`, and `APIMessage.VoiceServerUpdate` which gives you access to the token and endpoint. Once you have those 3, you want to create and login a voice WebSocket connection. To do this, create a `VoiceWsHandler` actor, and send it the `Login` message. When creating the actor, you have to decide which actor will receive the audio API messages. You can also specify another actor who will be the actor that receives the audio (I would recommend a dedicated actor with a pinned dispatcher(akka.io.pinned-dispatcher) for the actor that receives the audio).

## Sending the data
Once you have the WS actor set up, you should expect an `AudioAPIMessage.Ready` message sent to the actor which you specified should receive all the audio API messages. This message contains the `UDPHandler` actor, which is where you want to send your data. I would recommend setting up an actor just for sending the data. Before sending any data, you always have to send a `SetSpeaking(true)` message to the WS handler.

### The naive standard way
To send an audio packet, you use the `SendData` message. You probably want to use a pinned dispatcher(akka.io.pinned-dispatcher) if you choose to use this method. Even with a pinned dispatcher, however, you still need to remember that the akka timers, while lightweight, are not the most accurate. There is, however, a better way, although it requires a bit more state keeping and setup.

### Utilizing the buffer better
The `UDPHandler` actor comes packaged with a buffer. If it cannot send a message this instant, it sends the message as soon possible. Taking advantage of this, you can send one big message containing multiple packets every says 200 ms.

First, send a `BeginBurstMode` message to the UDP handler. From here in, the UDP handler sends a `DataRequest` packet to the sender of the `BeginBurstMode` whenever it reaches a certain threshold from the config. When your actor receives a `DataRequest` message from the UDP handler, it should send a `SendDataBurst` in the close future. Do note that it does not have to be now, and it should not be now all the time.

If just sending a burst whenever requested, this would be easy. However, you also need to keep track of how much audio you have sent in advance, and send more audio, or wait with sending audio until later based on that information. For example, if we send 12 packets of sound totalling 240ms, and then 30 ms after that receive another request for 12 packets of audio, we might want to wait a bit before sending those packets, to be sure we are not too far ahead.

### Other things to keep in mind
If you follow the advice above, creating an audio sender should hopefully not be too hard. One other thing you need to remember is that whenever you stop speaking, you need to send five packets of silence, use the code `udpHandler ! SendDataBurst(Seq.fill(5)(silence))` to easily do this. If you need a reference to how the different types of data sender might look like, you can look at [AudioSender](https://github.com/Katrix/AckCord/blob/master/ackCordLavaplayer/src/main/scala/net/katsstuff/ackcord/lavaplayer/AudioSender.scala) and [BurstingAudioSender](https://github.com/Katrix/AckCord/blob/master/ackCordLavaplayer/src/main/scala/net/katsstuff/ackcord/lavaplayer/BurstingAudioSender.scala) in the lavaplayer module. If you are looking for a good Discord music library, [LavaPlayer](https://github.com/sedmelluq/lavaplayer) is probably your best bet.