---
layout: docs
title: Data objects
---

# {{page.title}}

Being a library for Discord is not always easy, especially for all the data 
objects AckCord has to handle. Keeping everything as case classes might seem 
simple at first, but also means an upfront cost of decoding the JSON, and the 
chance that the decoder is just wrong. It also makes it harder to evolve the 
library in a binary compatible way. 

To get around these issues, AckCord instead keeps the JSON around, decoding each 
field as needed. This does mean that if AckCord has the wrong types for a field,
accessing it will throw an exception. It also means that you as the user can 
easily work around this issue until AckCord releases a new fixed version.

## Selecting fields manually
AckCord's data class field accessors generally call the function 
`DiscordObject#selectDynamic`. If a field is missing, or has the wrong type, 
nothing stops you as a user from calling this function yourself with the correct
field name and type.

## Retyping data
Calling `asInstanceOf` on a data object is often an error. Instead, what you 
might want is `retype`. `asInstanceOf` casts the data object to a different 
class, failing if the data object is not an instance of that class. `retype` 
meanwhile reinterprets the data object as a different type.

There are many uses of retyping data. The most common is that an object is 
all fields of `X`, in addition to fields `a`, `b` and `c`. This can be 
represented as a data object with accessors `a`, `b`, and `c`, and another 
accessor `x` which retypes the object to `X`.
