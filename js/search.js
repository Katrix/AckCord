// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "ActionBuilder deep dive (Advanced)",
      "url": "/docs/advanced/actionbuilders_deep.html",
      "content": "ActionBuilder deep dive You’ve probably used a bunch of action builders at this point. How do they work though, and how can you extend them. ActionFunction Everything is build on ActionFunction. At it’s core, an ActionFunction is a function def flow[A]: Flow[I[A], Either[Option[E], O[A]], NotUsed]. It represents a step that can be taken before the action is processed. Let’s go over that piece for piece. I[A] here is the input type to the function. For example CommandMessage. The A here is the parsed type. Such a step can either succeed (Right[O[A]]), or fails (Left[Option[E]]). If it fails, it might, but doesn’t have to return an error (E). The O[A] here represents the output type of this step, and the input type of the next one. For example, I could be CommandMessage and O could be GuildCommandMessage. ActionTransformer ActionTransformer is a simpler ActionFunction that always succeeds. If you have a FunctionK, you can easily lift it into an ActionTransformer using ActionTransformer.fromFuncK. There’s not much else to say about it. ActionBuilder ActionBuilder is an ActionFunction which is considered the final step of the chain of functions before it’s handed off to the user. Composing existing functions You’re completely free to compose the existing functions as you see fit. As AckCord doesn’t know the output type you want to create, most functions that create an ActionFunction takes a A =&gt; FunctionK[I, O] for the input type to the output type. Here the A type represents new info extracted by the ActionFunction, while I is the input type, and O is the output type. Use might for example look like this: val Command: CommandBuilder[UserCommandMessage, NotUsed] = baseCommandBuilder.andThen(CommandBuilder.nonBot { user =&gt; λ[CommandMessage ~&gt; UserCommandMessage](m =&gt; UserCommandMessage.Default(user, m)) })"
    } ,    
    {
      "title": "The`Events object (Advanced)",
      "url": "/docs/advanced/cache.html",
      "content": "The Events object Let’s look a bit more in-depth into how the Events object in AckCord works. The cache The main thing stored in here is the cache. Not a snapshot of the cache, just the cache. At its core, the cache is a sink, which takes cache updates, a source which supplies these cache updates, and a flow which applies the updates to the current state. Both of these streams can be materialized as many times as needed. Currently the only update types are APIMessageCacheUpdate and MiscCacheUpdate. Use MiscCacheUpdate if you want to modify the cache in some way yourself. APIMessage So, what is an APIMessage then? An APIMessage is a more straightforward type for working with cache updates, where the update type is APIMessageCacheUpdate. Publishing to the cache You can also publish other changes to the cache. In most cases, you can use the utility methods on the cache object itself (publishSingle, publishMany) Gateway events Something else that the events object also gives you access to is the raw events coming from the Discord gateway. These are not processed in any way. This can be both a positive and negative. Postive as you will always receive the event, even if something went wrong in creating the APIMessage object. Negative as you need to handle the raw version of the objects coming from the gateway. Publishing to the gateway While most requests are made through HTTP requests, there are some that are done through the gateway. The Cache object contains a Sink called sendGatewayPublish that you use, to publish these requests. The valid requests to send to this Sink are StatusUpdate, VoiceStateUpdate, VoiceServerUpdate and RequestGuildMembers."
    } ,    
    {
      "title": "CacheSnapshot",
      "url": "/docs/cachesnapshot.html",
      "content": "CacheSnapshot When reacting to changes in Discord, there are two main places you can get data from. The first is from the change event itself. This is for example the message created in the APIMessage.MessageCreate event. The second is from the cache, which up to this point we have been ignoring (hence the ignore in onEventSideEffectsIgnore). The signature of the non-ignore methods is CacheSnapshot =&gt; PartialFunction[APIMessage, Unit]. It might look a little weird, but is perfectly valid. Here is an example printing the channel of all new messages. All lookups in the cache return Options. client.onEventSideEffects { c =&gt; { case msg: APIMessage.MessageCreate =&gt; println(c.getTextChannel(msg.message.channelId)) } } To increase ergonomics, there are also many ways to get objects from the cache without asking it directly. This assumes an implicit cache. The first is following ids. You can call resolve on any id to get the object it corresponds to. In some cases there might be multiple resolve methods, either for performance reasons, or to get different objects that both use the same id. client.onEventSideEffects { implicit c =&gt; { case msg: APIMessage.MessageCreate =&gt; println(msg.message.channelId.resolve) } } In addition to that, in many cases you can find methods on the object you’re interacting with taking a cache snapshot, and returning an object directly. client.onEventSideEffects { implicit c =&gt; { case APIMessage.MessageCreate(_, message: GuildGatewayMessage, _, _) =&gt; println(message.guild) } } CacheState Sometimes when dealing with events, you want to get a cache from just before the current event happened. In those cases it’s time to use the CacheState. All APIMessages contain one. In the example above it was the value we ignored on APIMessage.MessageCreate. It stores the current and the previous CacheSnapshot. For example, to get the message before it was edited in APIMessage.MessageUpdate, we do something like this. client.onEventSideEffects { c =&gt; { case APIMessage.MessageUpdate(_, messageId, _, CacheState(current, previous), _) =&gt; println(messageId.resolve(previous)) } } CacheSnapshots are always immutable. It’s perfectly valid to both store them away for later, or have multiple of them."
    } ,    
    {
      "title": "Commands",
      "url": "/docs/commands.html",
      "content": "Commands Commands are probably the most common way to interact with bots. AckCord comes with a built in commands framework. In use it resembles the API exposed by EventsController (It’s probably more accurate to say the events controller resembles the commands controller). It’s use looks something like this. import ackcord.commands._ import ackcord.syntax._ import akka.NotUsed class MyCommands(requests: Requests) extends CommandController(requests) { val hello: NamedCommand[NotUsed] = Command .named(Seq(\"m!\"), Seq(\"hello\")) .withRequest(m =&gt; m.textChannel.sendMessage(s\"Hello ${m.user.username}\")) } Use named to give the command a name. You can also name it later if you don’t name it here. For the execution of the command itself, you have all the same options you had with the events controllers. For simple commands withRequest, which sends the return request of the command automatically, is probably enough. Like with events we need to register our commands. We do this using an CommandConnector. You can find one at DiscordClient#commands. val myCommands = new MyCommands(client.requests) client.commands.runNewNamedCommand(myCommands.hello) If you have many named commands, you can bulk register them all using bulkRunNamed. Help command AckCord comes built in with a partially premade help command controller. Complete it by extending HelpCommand and implementing the missing functions. Each command needs to also be registered with the help command. This can be done either by calling HelpCommand#registerCommand, or through the command connector through runNewNamedCommandWithHelp and bulkRunNamedWithHelp. There are many useful CommandBuilders. Make sure to give them all a look."
    } ,    
    {
      "title": "Commands deep dive (Advanced)",
      "url": "/docs/advanced/commands_deep.html",
      "content": "Commands deep dive This section assumes you have read the action builders deep dive. All of the stuff in there also applies here. PrefixParser The simplest way to name a command builder is to simply call the named function on it. You can however also delay that until registration. At that point the easy way to get a name is to call PrefixParser#structured, which returns a PrefixParser. You are also free to construct a PrefixParser yourself. At it’s core it’s simply a function (CacheSnapshot, Message, ExecutionContext) =&gt; Future[MessageParser[Unit]]. The function will be evaluated and tried for each incoming message. If the message parser succeeds, the remaining string will be used for the command itself. If it fails, it will discard the message. StructuredPrefixParser There is also a slightly more constrained version of PrefixParser called StructuredPrefixParser. This is what you are using when you call the named function on a function. This variant as a bit more of a predefined sturcture, but can in return more easily be included in a help command. Custom error handling The default behavior when a command fails is to print an error message. If you want to instead do something else, call CommandConnector#newCommandWithErrors and friends. These will give your a source of command errors, that you can then handle however you want."
    } ,    
    {
      "title": "Custom requests (Advanced)",
      "url": "/docs/advanced/custom_request.html",
      "content": "Custom requests Sometimes Discord releases a new endpoint that you want to use, but AckCord hasn’t added it yet, or maybe you want to use an internal endpoint. How can you use AckCord’s code to perform the request? Recall that AckCord’s requests are represented as objects. To create a new request, you need a new subclass of this request type. While it’s possible to extend Request directly, in most cases you probably want to extend RESTRequest or one of it’s children. There are many options here, so let’s go over them all: NoParamsRequest: Use this if it doesn’t take a body. ReasonRequest: Use this if the action you’re perfoming can leave an audit log and you want a special message in that audit log entry. NoNiceResponseRequest: Most requests have both a raw representation and a nicer representation. Not all though. Use this when there is no such distinction. NoParamsReasonRequest: Combines NoParamsRequest and ReasonRequest. NoNiceResponseReasonRequest: Combines NoNiceResponseRequest and ReasonRequest. NoParamsNiceResponseRequest: Combines NoParamsRequest and NoNiceResponseRequest. NoParamsNiceResponseReasonRequest: Combines NoParamsNiceResponseReques and ReasonRequest. NoResponseRequest: Used for requests that return 204. NoResponseReasonRequest: Combines NoResponseRequest and ReasonRequest. NoParamsResponseRequest: Combines NoResponseRequest and NoParamsRequest. NoParamsResponseReasonRequest: Combines NoParamsResponseRequest and ReasonRequest. RESTRequest: If nothing else works You don’t have to choose what first best for your case though. Most of these just help reduce boilerplate. When defining the request itself, you want it to take the needed body, an encoder for the body, a decoder for the result, and the for the request to go to. Everything you need to make the route can be found in ackcord.requests.Routes. import ackcord.requests.Routes._ import akka.http.scaladsl.model.HttpMethods._ //In most cases you either want to start at base val foo = base / \"foo\" // foo: Route = Route( // uriWithMajor = \"https://discord.com/api/v9/foo\", // uriWithoutMajor = \"https://discord.com/api/v9/foo\", // applied = Uri( // scheme = \"https\", // authority = Authority( // host = NamedHost(address = \"discord.com\"), // port = 0, // userinfo = \"\" // ), // path = Slash( // tail = Segment( // head = \"api\", // tail = Slash( // tail = Segment( // head = \"v9\", // tail = Slash(tail = Segment(head = \"foo\", tail = )) // ) // ) // ) // ), // rawQueryString = None, // fragment = None // ) // ) //Or one of the already defined routes val bar = guild / \"bar\" // bar: RouteFunction[ackcord.data.package.GuildId] = RouteFunction( // route = scala.Function1$$Lambda$11026/0x0000000802a1bad0@221f8343 // ) //If you have some parameter, you you the coresponding parameter type val barUser = bar / userId // barUser: RouteFunction[(ackcord.data.package.GuildId, ackcord.data.package.UserId)] = RouteFunction( // route = scala.Function2$$Lambda$10239/0x0000000802867878@75ffc972 // ) case class Bin(binName: String) //If there is no parameter type for what you want, you can create it //In most cases you want minor parameters val bin = new MinorParameter[Bin](\"bin\", _.binName) // bin: MinorParameter[Bin] = ackcord.requests.Routes$MinorParameter@4830fbb //Use it like any other parameter val guildBin = guild / bin // guildBin: RouteFunction[(ackcord.data.package.GuildId, Bin)] = RouteFunction( // route = scala.Function2$$Lambda$10239/0x0000000802867878@5ef00cfc // ) //Once you've defined the path of the requst, call toRequest with the method to //get a useable request route val getGuildBin = guildBin.toRequest(GET) // getGuildBin: (ackcord.data.package.GuildId, Bin) =&gt; ackcord.requests.RequestRoute = shapeless.ops.FnFromProductInstances$$Lambda$11049/0x0000000802a58f30@42923e4e // Queries are also parameters val height = new QueryParameter[Int](\"height\", _.toString) // height: QueryParameter[Int] = ackcord.requests.Routes$QueryParameter@363df0ff //Use +? to use a query parameter val binWithHeight = guildBin +? height // binWithHeight: QueryRouteFunction[((ackcord.data.package.GuildId, Bin), Option[Int])] = QueryRouteFunction( // route = scala.Function2$$Lambda$10239/0x0000000802867878@6bb2613f // ) //You can also define a query parameter inline with query val binWithWidth = guildBin +? query[Int](\"width\", _.toString) // binWithWidth: QueryRouteFunction[((ackcord.data.package.GuildId, Bin), Option[Int])] = QueryRouteFunction( // route = scala.Function2$$Lambda$10239/0x0000000802867878@2ed11407 // ) Look at the existing requests and routes in AckCord to get a better idea of how to do it yourself."
    } ,    
    {
      "title": "Events",
      "url": "/docs/events.html",
      "content": "Events Whenever something happens in Discord, it sends an event to your client. This can for example be: someone sends, updates, or deletes a message, your bot joins a new guild, or someone creates a new channel. There are two main ways to listen to these events. The first is through DiscordClient#onEventSideEffects and friends. We’ll use DiscordClient#onEventSideEffectsIgnore here, and go over the other forms in the next section. client.onEventSideEffectsIgnore { case msg: APIMessage.MessageCreate =&gt; println(msg.message.content) } There is nothing that decides where you have to start listening to an event. You can do it anywhere, even inside another listener. The return type of this method call is EventRegistration[Mat], but most of often just EventRegistration[NotUsed]. This value lets you know when an event listener stops, and a way to stop it. The second way to listen for events is using an EventsController. If you’ve ever used controllers and actions in the Play framework, then the base idea is the same. To start you pick an event listener builder that best fits your need. Some examples are Event, TextChannelEvent and GuildUserEvent. Depending on which event you chose, it might contain some extra information surrounding the event. You then call to with the event type you are listening to. From there you call one of the methods on it that bests suits you need, like withSideEffects, withRequest, async, and so on. class MyListeners(requests: Requests) extends EventsController(requests) { val onCreate = TextChannelEvent.on[APIMessage.MessageCreate].withSideEffects { m =&gt; println(m.event.message.content) } } These listeners also need to be registered. That can be done using DiscordClient#registerListener. val myListeners = new MyListeners(client.requests) client.registerListener(myListeners.onCreate) Here is a small example that illustrates the fact that you can define and use your listeners anywhere. There is nothing special about them. val MessageEvent: EventListenerBuilder[TextChannelEventListenerMessage, APIMessage.MessageCreate] = TextChannelEvent.on[APIMessage.MessageCreate] val createListeners: EventListener[APIMessage.MessageCreate, NotUsed] = MessageEvent.withSideEffects { m =&gt; val startMessage = m.event.message if (startMessage.content.startsWith(\"start listen \")) { val inChannel = m.channel.id val identifier = startMessage.content.replaceFirst(\"start listen \", \"\") val listener = client.registerListener(MessageEvent.withSideEffects { m =&gt; if (m.channel.id == inChannel) { println(s\"$identifier: ${m.event.message.content}\") } }) //We need lazy and an explicit type here to make Scala happy lazy val stopper: EventRegistration[NotUsed] = client.registerListener(MessageEvent.withSideEffects { m =&gt; if (m.channel.id == inChannel &amp;&amp; m.event.message.content == \"stop listen \" + identifier) { listener.stop() stopper.stop() } }) //Initialize stopper stopper } } Note that there is no defined order for which event handler will receive an event first. If you register an event handler inside another event handler, it might or might not receive the event that caused its registration."
    } ,    
    {
      "title": "Your first bot (Low level)",
      "url": "/docs/low-level/first_bot.html",
      "content": "Your first bot Let’s build the simplest low level bot you can using AckCord. The only thing it will do is to log in, and print a line to the console when it has done so. First add AckCord to your project by adding these statements to your build.sbt file. libraryDependencies += \"net.katsstuff\" %% \"ackcord-core\" % \"0.18.1\" Most of these examples assume these two imports. import ackcord._ import ackcord.data._ Next we need a bot token. You can get one by going to https://discord.com/developers/applications, creating a new application, and then creating a bot for your application. val token = \"&lt;token&gt;\" //Your Discord token. Be very careful to never give this to anyone else When working with the low level API, you’re the one responsible for setting stuff up, and knowing how it works. Therefore there isn’t one correct way to do this. What I will show here is a very barebones way to do it, but ultimately you’re the one that will have to decide how to do it. First we need an actor system. In most applications you probably already have one lying around. import akka.actor.typed._ import akka.actor.typed.scaladsl._ import akka.stream.scaladsl.Sink import akka.util.Timeout import scala.concurrent.duration._ import ackcord.requests.{Ratelimiter, RatelimiterActor, RequestSettings} implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.ignore, \"AckCord\") import system.executionContext Next we create the Cache, and the RequestHelper. The Cache helps you know when stuff happens, and keeps around the changes from old things that have happened. The RequestHelper helps you make stuff happen. I’d recommend looking into the settings used when creating both the Cache and RequestHelper if you want to fine tune your bot. val cache = Events.create() val ratelimitActor = system.systemActorOf(RatelimiterActor(), \"Ratelimiter\") val requests = { implicit val timeout: Timeout = 2.minutes //For the ratelimiter new Requests( RequestSettings( Some(BotAuthentication(token)), Ratelimiter.ofActor(ratelimitActor) ) ) } Now that we have all the pieces we want, we can create our event listener. In the low level API, events are represented as a Source you can materialize as many times as you want. cache .subscribeAPI .collectType[APIMessage.Ready] .to(Sink.foreach(_ =&gt; println(\"Now ready\"))) .run() Finally we can create our GatewaySettings and start the shard. val gatewaySettings = GatewaySettings(token) DiscordShard.fetchWsGateway.foreach { wsUri =&gt; val shard = system.systemActorOf(DiscordShard(wsUri, gatewaySettings, cache), \"DiscordShard\") //shard ! DiscordShard.StartShard }"
    } ,    
    {
      "title": "Your first bot",
      "url": "/first_bot.html",
      "content": "Your first bot Let’s build the simplest bot you can using AckCord. The only thing it will do is to log in, and print a line to the console when it has done so. First add AckCord to your project by adding these statements to your build.sbt file. resolvers += Resolver.JCenterRepository libraryDependencies += \"net.katsstuff\" %% \"ackcord\" % \"0.18.1\" Most of these examples assume these imports. import ackcord._ import ackcord.data._ import scala.concurrent.Await import scala.concurrent.duration.Duration Next we need a bot token. You can get one by going to https://discord.com/developers/applications, creating a new application, and then creating a bot for your application. val token = \"&lt;token&gt;\" //Your Discord token. Be very careful to never give this to anyone else Logging in The first thing you need when logging in is a ClientSettings instance. For you first bot, the defaults are fine, just pass it your token. This value also contains a lot of other useful stuff, so keep it around. This time you’ll only use the execution context it contains, and create a client. Using that client you can then listen for specific messages and events. Once you have set everything up, you call login. val clientSettings = ClientSettings(token) //The client settings contains an excecution context that you can use before you have access to the client //import clientSettings.executionContext //In real code, please dont block on the client construction val client = Await.result(clientSettings.createClient(), Duration.Inf) //The client also contains an execution context //import client.executionContext client.onEventSideEffectsIgnore { case _: APIMessage.Ready =&gt; println(\"Now ready\") } //client.login() And that’s it. You’ve now created your first bot. Now, this is probably not the only thing you want to do. Next I would recommend you to check out the pages for events, requests, and CacheSnapshot as well. Going over the commands API is probably also a smart thing to do."
    } ,    
    {
      "title": "Expanding AckCord",
      "url": "/docs/advanced/",
      "content": "Expanding AckCord Sometimes when using the low level API you want to go the extra mile. Reach down and touch what would almost be the internals. That’s what this section is for. Giving you a deep understanding of topics in AckCord, or teach you how to expand AckCord in new ways."
    } ,    
    {
      "title": "The low level API",
      "url": "/docs/low-level/",
      "content": "The low level API So far you have seen a high level overview of how AckCord works. Maybe that’s enough for you. It’s perfectly possible to write a bot entirely using the high level API. Sometimes however, whether that be for performance, flexibility or something else, you want to reach down closer to the metal. In the next few pages we will go over how to interact with AckCord from a lower level, often using Akka directly instead of hiding it away. Knowledge about how Akka works is highly recommended from here on. Do note that you don’t have to make a choice between the low or high level API for all of your bot. The low level API is easily accessible in most places from the high level API. Also note that I’m assuming that you are familiar with the high level API before reading this section. While there are differences, many parts are also the same. For commands for example, the only difference is that you need to create your own CommandConnector."
    } ,    
    {
      "title": "AckCord",
      "url": "/",
      "content": "AckCord You do what you want, exactly how you want it. AckCord is a Scala Discord library, powered by Akka. AckCord’s focus is on letting you choose the level of abstraction you want, without sacrificing speed. Want to work with the raw events from the gateway? Works for that. Maybe you don’t want to bother with any of the underlying implementation and technicalities. Works for that too. Only interested in the REST requests? Pull in that module and ignore the rest. AckCord is fast, reactive, modular, and clean, focusing on letting you write good code. Add AckCord to your project by adding these statements to your build.sbt file. libraryDependencies += \"net.katsstuff\" %% \"ackcord\" % \"0.18.1\" //For high level API, includes all the other modules libraryDependencies += \"net.katsstuff\" %% \"ackcord-core\" % \"0.18.1\" //Low level core API libraryDependencies += \"net.katsstuff\" %% \"ackcord-commands\" % \"0.18.1\" //Commands API libraryDependencies += \"net.katsstuff\" %% \"ackcord-lavaplayer-core\" % \"0.18.1\" //Low level lavaplayer API More information For more information, either see the the examples or the ScalaDoc. Or you can just join the Discord server (we got cookies)."
    } ,    
    {
      "title": "Interactions",
      "url": "/docs/interactions.html",
      "content": "Interactions Interactions are the new way of adding functionality to your application. To use components, you need to have Requests available in scope. Buttons import ackcord.interactions._ import ackcord.interactions.commands._ // This import is needed for the .onClick() method import ackcord.interactions.components._ class MyCommands(requests: Requests) extends CacheApplicationCommandController(requests) { // Create a button SlashCommand.command(\"click\", \"Click me!!\") { _ =&gt; sendMessage( \"Go on, click it!\", components = Seq( ActionRow.of( Button .text(\"Click Me!\", \"clickme\") .onClick(new AutoButtonHandler[ComponentInteraction](_, requests) { def handle(implicit interaction: ComponentInteraction): InteractionResponse = sendMessage(\"You clicked me, you're a star!\") }) ) ) ) } // Have 2 buttons SlashCommand.command(\"which\", \"Yes, or No?\") { _ =&gt; sendMessage( \"Which will you choose...\", components = Seq( ActionRow.of( Button .text(\"Yes\", \"yes\") .onClick(new AutoButtonHandler[ComponentInteraction](_, requests) { def handle(implicit interaction: ComponentInteraction): InteractionResponse = sendMessage(\"You chose yes!\") }), Button .text(\"No\", \"no\") .onClick(new AutoButtonHandler[ComponentInteraction](_, requests) { def handle(implicit interaction: ComponentInteraction): InteractionResponse = sendMessage(\"You chose no?\") }) ) ) ) } } Selections Selections allow you to choose from preset options. class MyOtherCommands(requests: Requests) extends CacheApplicationCommandController(requests) { SlashCommand.command(\"click\", \"Click me!!\") { _ =&gt; sendMessage( \"Go on, click it!\", components = Seq( ActionRow.of( SelectMenu( List( SelectOption(\"I'm Yes!\", \"yes\"), SelectOption(\"I'm No!\", \"no\") ), placeholder = Some(\"Are you sure?\") ) ) ) ) } }"
    } ,    
    {
      "title": "The many modules of AckCord",
      "url": "/modules.html",
      "content": "The many modules of AckCord While the modules listed at the start are the main modules you want to depend on, AckCord has many more if you feel that you don’t need the full package. Here is a map of all the modules, and how they depend on each other +----&gt; voice ------&gt; lavaplayer-core -+ | ^ | | | v data +----&gt; gateway --&gt; core ----+----&gt; ackcord | ^ ^ | | | +----&gt; requests ----+-&gt; commands -----+ Make sure to add the following line to your SBT file if you’re using Lavaplayer: resolvers += Resolver.JCenterRepository ackcord-data libraryDependencies += \"net.katsstuff\" %%% \"ackcord-data\" % \"0.18.1\" Provides access to all the data objects, encoders and decoders used by AckCord. This module is also crosscompiled for Scala.Js. Wonderful if you want to build a web panel for your bot in Scala, and don’t want to redefine all the data objects. ackcord-requests libraryDependencies += \"net.katsstuff\" %% \"ackcord-requests\" % \"0.18.1\" Contains all the requests in AckCord. ackcord-gateway libraryDependencies += \"net.katsstuff\" %% \"ackcord-gateway\" % \"0.18.1\" Contains the code for the AckCord gateway implementation. Depend on this if you want to build custom logic from the gateway and on. ackcord-voice libraryDependencies += \"net.katsstuff\" %% \"ackcord-voice\" % \"0.18.1\" Contains all the code for the voice stuff in AckCord. ackcord-commands libraryDependencies += \"net.katsstuff\" %% \"ackcord-commands\" % \"0.18.1\" The commands API module. ackcord-core libraryDependencies += \"net.katsstuff\" %% \"ackcord-core\" % \"0.18.1\" The low level API module of AckCord. Provides the in memory cache. ackcord-lavaplayer-core libraryDependencies += \"net.katsstuff\" %% \"ackcord-lavaplayer-core\" % \"0.18.1\" Provides an implementation to the the voice module using lavaplayer and core. To use, first create an instance of the LavaplayerHandler actor. This actor should be shared for an entire guild. Check the objects in the companion object of LavaplayerHandler for the valid messages you can send it. ackcord libraryDependencies += \"net.katsstuff\" %% \"ackcord\" % \"0.18.1\" The high level API, includes all the other modules."
    } ,      
    {
      "title": "Making requests (Low level)",
      "url": "/docs/low-level/requests.html",
      "content": "Making requests Making requests in the low level happens just as in the high level. Running them is however slighly different. Just like the high level API has RequestsHelper to run requests, The low level API has Requests. First off, it provides two FlowWithContexts from requests to request answers. flowWithoutRateLimits: A flow that doesn’t follow ratelimits to slow down your streams to the requests. Be careful using this one. I’m not responsible if your bot gets banned for not following the ratelimits. flow: Your normal flow that does what you would expect. It can also be configured using implicit RequestProperties. Using these you can make it Use ordered execution for all requests that goes in. Retry failed requests. There are also a lot of helpers to deal with common tasks like: sinkIgnore: Sink that runs the requests and ignores the result. flowSuccess: Flow that only returns successful requests. single: Creates a source containing a single request answer from a request. many: Creates a source containing a many request answer from many request."
    } ,    
    {
      "title": "Making Requests",
      "url": "/docs/requests.html",
      "content": "Making Requests Requests are how most bot actions are done. Stuff like sending messages, creating channels and managing users. AckCord represents every request as an object you construct. There are many ways to send an request, but the most common are through the end action on controllers, and using RequestsHelper. To create a request object, you can either just construct it normally, or use the syntax package. import ackcord.requests._ import ackcord.syntax._ client.onEventSideEffects { implicit c =&gt; { case APIMessage.ChannelCreate(_, channel: TextGuildChannel, _, _) =&gt; //There is also CreateMessage.mkContent for this specific pattern val constructManually = CreateMessage(channel.id, CreateMessageData(content = \"Hello World\")) val usingSyntax = channel.sendMessage(content = \"Hello World\") } } Next you need to send the request. For this example we will use RequestsHelper. This object can be found on the client. That also means it’s time to move away from onEventSideEffects. Even though sending requests can never return option, the run command will return an OptFuture[A]. OptFuture[A] is a wrapper around Future[Option[A]]. This is done due to the many cache lookup methods that return Options. When your event handler returns OptFuture[Unit], you’re recommended to use DiscordClient#onEventAsync and ActionBuilder#asyncOpt instead. client.onEventAsync { implicit c =&gt; { case APIMessage.ChannelCreate(_, channel: TextGuildChannel, _, _) =&gt; client.requestsHelper.run(channel.sendMessage(content = \"Hello World\")).map(_ =&gt; ()) } } The RequestsHelper object also contains lots of small helpers to deal with OptFuture[A] and requests. client.onEventAsync { implicit c =&gt; { case msg: APIMessage.ChannelCreate =&gt; import client.requestsHelper._ for { //optionPure lifts an Option into the dsl guildChannel &lt;- optionPure(msg.channel.asGuildChannel) guild &lt;- optionPure(guildChannel.guild) //runOption runs an Option[Request]. msg &lt;- runOption(guild.textChannels.headOption.map(_.sendMessage(\"FIRST\"))) //Running a request without using the extra syntax _ &lt;- run(CreateMessage.mkContent(msg.channelId, \"Another message\")) } yield () } }"
    } ,      
    {
      "title": "Slash Commands",
      "url": "/docs/slashcommands.html",
      "content": "{{page.title}} Slash Commands are the new way of interacting with Discord bots. AckCord comes with a built-in slash commands framework. Commands The simplest command you can make is a /ping command. import ackcord.interactions._ import ackcord.interactions.commands._ class MyPingCommands(requests: Requests) extends CacheApplicationCommandController(requests) { val pongCommand = SlashCommand.command(\"ping\", \"Check if the bot is alive\") { _ =&gt; sendMessage(\"Pong\") } } Arguments class MyParameterCommands(requests: Requests) extends CacheApplicationCommandController(requests) { // Single argument val echoCommand = SlashCommand .withParams(string(\"message\", \"The message to send back\")) .command(\"echo\", \"Echoes a message you send\") { implicit i =&gt; sendMessage(s\"ECHO: ${i.args}\") } // Multiple arguments val multiArgsCommand = SlashCommand .withParams(string(\"message\", \"The message to send back\") ~ string(\"intro\", \"The start of the message\")) .command(\"echoWithPrefix\", \"Echoes a message you send\") { implicit i =&gt; sendMessage(s\"${i.args._1}: ${i.args._2}\") } // Optional arguments val optArgsCommand = SlashCommand .withParams(string(\"message\", \"The message to send back\").notRequired) .command(\"echoOptional\", \"Echoes an optional message you send\") { implicit i =&gt; sendMessage(s\"ECHO: ${i.args.getOrElse(\"No message\")}\") } } Autocomplete Discord allows you to autocomplete arguments to allow the user to enter better arguments. This command will bring a small menu up with suggestions for the arguments, it will show 3 options, each a differently multiplied version of the number you have initially typed. class MyAutocompleteCommands(requests: Requests) extends CacheApplicationCommandController(requests) { val autocompleteCommand = SlashCommand .withParams(string(\"auto\", \"An autocomplete parameter\").withAutocomplete(s =&gt; Seq(s * 2, s * 3, s * 4))) .command(\"simple-autocomplete\", \"A simple autocomplete command\") { i =&gt; sendMessage(s\"Res: ${i.args}\") } } Async Async commands allow you to inform the user there will be a slight delay before the bot responds, useful for commands that fetch external resources or do a lot of work. class MyAsyncCommands(requests: Requests) extends CacheApplicationCommandController(requests) { // This command will very quickly show a loading message in discord. val asyncCommand = SlashCommand.command(\"async\", \"An async test command\") { implicit i =&gt; async(implicit token =&gt; sendAsyncMessage(\"Async message\")) } } This will edit the message you’ve sent, after it has been received in Discord but will not show the user a loading message. class MyAsyncEditCommands(requests: Requests) extends CacheApplicationCommandController(requests) { // This command will show a loading message in discord, send a message and edit the original message (which would mark the end of the async interaction). val asyncEditCommand = SlashCommand .withParams(string(\"par1\", \"The first parameter\") ~ string(\"par2\", \"The second parameter\")) .command(\"asyncEdit\", \"An async edit test command\") { implicit i =&gt; sendMessage(\"An instant message\").doAsync { implicit token =&gt; editOriginalMessage(content = JsonSome(\"An instant message (with an edit)\")) } } } Command groups Discord allow you to group commands together, this is useful for when you have a lot of commands that are similar. This example will register the commands: /group foo and /group bar class MyGroupedCommands(requests: Requests) extends CacheApplicationCommandController(requests) { val groupCommand = SlashCommand.group(\"group\", \"Group test\")( SlashCommand.command(\"foo\", \"Sends foo\")(_ =&gt; sendMessage(\"Foo\")), SlashCommand.command(\"bar\", \"Sends bar\")(_ =&gt; sendMessage(\"Bar\")) ) } Registering commands with Discord You need to register slash commands with discord for them to appear, it can be done like this. Commands that are globally registered can take up to an hour to propagate to all servers so using guild commands in development is recommended. val myPingCommands = new MyPingCommands(client.requests) val myParameterCommands = new MyParameterCommands(client.requests) val myAutocompleteCommands = new MyAutocompleteCommands(client.requests) val myAsyncCommands = new MyAsyncCommands(client.requests) val myAsyncEditCommands = new MyAsyncEditCommands(client.requests) client.onEventSideEffectsIgnore { case msg: APIMessage.Ready =&gt; // Create the commands globally in all discords. InteractionsRegistrar.createGlobalCommands( msg.applicationId, // Client ID client.requests, replaceAll = true, // Boolean whether to replace all existing // CreatedGuildCommand* myPingCommands.pongCommand, myParameterCommands.echoCommand, myParameterCommands.multiArgsCommand, myParameterCommands.optArgsCommand ) val myGuildId = GuildId(\"&lt;some id here&gt;\") // Create the commands in a specific discord. InteractionsRegistrar.createGuildCommands( msg.applicationId, // Client ID myGuildId, // Guild ID client.requests, replaceAll = true, // Boolean whether to replace all existing // CreatedGuildCommand* myAutocompleteCommands.autocompleteCommand, myAsyncCommands.asyncCommand, myAsyncEditCommands.asyncEditCommand ) } Registering commands with Ackcord When you have created all your slash commands and registered them with discord you can register them with ackcord. client.onEventSideEffectsIgnore { case msg: APIMessage.Ready =&gt; client.runGatewayCommands(msg.applicationId.asString)( myParameterCommands.echoCommand, myParameterCommands.multiArgsCommand, myParameterCommands.optArgsCommand, myAutocompleteCommands.autocompleteCommand, myAsyncCommands.asyncCommand, myAsyncEditCommands.asyncEditCommand ) }"
    } ,    
    {
      "title": "Snowflake types",
      "url": "/docs/snowflakes.html",
      "content": "{{page.title}} Unlike other Discord libraries, AckCord doesn’t have one specific snowflake type, instead it uses a type SnowflakeType[A] to refer to something of type A. Most of these types also have aliases, like UserId being an alias for SnowflakeType[User]. Converting to snowflake To convert a long or string(prefer string) to a snowflake, use the apply method on the companion object. For example: UserId(0L) You can also use this to “cast” one snowflake type to another. For example: RoleId(guildId). Raw snowflakes In some cases it’s not possible to give a concrete type to the snowflake. In those cases RawSnowflake is used instead, an alias for SnowflakeType[Any]. In most cases you’ll have to cast this to what you need yourself. Channel types For channels, AckCord will try to use the most specific type possible. In most cases this will work fine, but in some cases you’ll have to cast. As casting is much more common with channel id types, they have a special method asChannelId. To do this, and also help with type inference a bit more. Example: val channelId: ChannelId = ChannelId(0L) val c: CacheSnapshot = ??? c.getTextChannel(channelId.asChannelId)"
    } ,    
    {
      "title": "Sending custom sound (Advanced)",
      "url": "/docs/advanced/sound.html",
      "content": "Sending custom sound First of, let me issue a warning. This page is for if you want to implement your own sound provider. If all you’re interested in is sending music or similar, take a look at the lavaplayer module. If you’re instead interested in using something other than Lavaplayer, read on. With that out of the way, let’s talk sound. If you want to send sound with AckCord, you currently have to wire some stuff up yourself. AckCord still hides most of the implementations, but you are assumed to have some knowledge of how sound sending works in Discord. If you are not familiar with this, read the Discord docs for voice connections. Establishing a connection The first thing you need to do is to tell Discord that you want to begin speaking and sending audio. You do this by sending a VoiceStateUpdate message to the gateway. From there you wait for two events. APIMessage.VoiceStateUpdate which gives you access to your VoiceState and as such your sessionId, and APIMessage.VoiceServerUpdate which gives you access to the token and endpoint. Once you have those 3, you want to create and login a voice WebSocket connection. To do this, create a VoiceHandler actor. When creating the actor, you have to decide which actor will receive the audio API messages. Lastly you also need a source of the voice data which will be sent to Discord. AckCord does no processing on the data. That’s your responsibility. Sending the data Once you have the WS actor set up, you should expect an AudioAPIMessage.Ready message sent to the actor which you specified should receive all the audio API messages. At this point you should make the source you passed to the VoiceHandler begin to produce sound. Before sending any data, you always have to send a SetSpeaking(true) message to the voice handler. Other things to keep in mind If you follow the advice above, creating an audio sender should hopefully not be too hard. One other thing you need to remember is that whenever you stop speaking, you need to send five packets of silence. If you need more help, take a look at AckCord’s lavaplayer implementation."
    }    
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
