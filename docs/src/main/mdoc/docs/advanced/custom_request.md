---
layout: docs title: Custom requests (Advanced)
---

# Custom requests

Sometimes Discord releases a new endpoint that you want to use, but AckCord hasn't added it yet, or maybe you want to
use an internal endpoint. How can you use AckCord's code to perform the request?

Recall that AckCord's requests are represented as objects. To create a new request, you need a new subclass of this
request type. While it's possible to extend `Request` directly, in most cases you probably want to extend `RESTRequest`
or one of it's children.

There are many options here, so let's go over them all:

* `NoParamsRequest`: Use this if it doesn't take a body.
* `ReasonRequest`: Use this if the action you're perfoming can leave an audit log and you want a special message in that
  audit log entry.
* `NoNiceResponseRequest`: Most requests have both a raw representation and a nicer representation. Not all though. Use
  this when there is no such distinction.
* `NoParamsReasonRequest`: Combines `NoParamsRequest` and `ReasonRequest`.
* `NoNiceResponseReasonRequest`: Combines `NoNiceResponseRequest` and `ReasonRequest`.
* `NoParamsNiceResponseRequest`: Combines `NoParamsRequest` and `NoNiceResponseRequest`.
* `NoParamsNiceResponseReasonRequest`: Combines `NoParamsNiceResponseReques` and `ReasonRequest`.
* `NoResponseRequest`: Used for requests that return 204.
* `NoResponseReasonRequest`: Combines `NoResponseRequest` and `ReasonRequest`.
* `NoParamsResponseRequest`: Combines `NoResponseRequest` and `NoParamsRequest`.
* `NoParamsResponseReasonRequest`: Combines `NoParamsResponseRequest` and `ReasonRequest`.
* `RESTRequest`: If nothing else works

You don't have to choose what first best for your case though. Most of these just help reduce boilerplate.

When defining the request itself, you want it to take the needed body, an encoder for the body, a decoder for the
result, and the for the request to go to.

Everything you need to make the route can be found in `ackcord.requests.Routes`.

```scala mdoc
import ackcord.requests.Routes._
import akka.http.scaladsl.model.HttpMethods._

//In most cases you either want to start at base
val foo = base / "foo"

//Or one of the already defined routes
val bar = guild / "bar"

//If you have some parameter, you you the coresponding parameter type
val barUser = bar / userId

case class Bin(binName: String)

//If there is no parameter type for what you want, you can create it
//In most cases you want minor parameters
val bin = new MinorParameter[Bin]("bin", _.binName)

//Use it like any other parameter
val guildBin = guild / bin

//Once you've defined the path of the requst, call toRequest with the method to 
//get a useable request route
val getGuildBin = guildBin.toRequest(GET)

// Queries are also parameters
val height = new QueryParameter[Int]("height", _.toString)

//Use +? to use a query parameter
val binWithHeight = guildBin +? height

//You can also define a query parameter inline with query
val binWithWidth = guildBin +? query[Int]("width", _.toString)
```

Look at the existing requests and routes in AckCord to get a better idea of how to do it yourself.
