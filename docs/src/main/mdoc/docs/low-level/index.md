---
layout: docs
title: The low level API
---

# {{page.title}}
So far you have seen a high level overview of how AckCord works. Maybe that's 
enough for you. It's perfectly possible to write a bot entirely using the high
level API. Sometimes however, whether that be for performance, flexibility or 
something else, you want to reach down closer to the metal.

In the next few pages we will go over how to interact with AckCord from a lower 
level, often using Akka directly instead of hiding it away. Knowledge about how 
Akka works is highly recommended from here on.

Do note that you don't have to make a choice between the low or high level API 
for all of your bot. The low level API is easily accessible in most places from 
the high level API.

Also note that I'm assuming that you are familiar with the high level API before 
reading this section. While there are differences, many parts are also the same. 
For commands for example, the only difference is that you need to create your 
own `CommandConnector`.
