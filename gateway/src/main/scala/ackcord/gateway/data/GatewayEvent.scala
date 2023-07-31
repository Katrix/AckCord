//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.gateway.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/gateway/data/GatewayEvent.yaml

import ackcord.data._
import ackcord.data.base._
import io.circe.Json

object GatewayEvent extends GatewayEventBase.TopMixin {

  class Dispatch(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayDispatchEvent.UnknownGatewayDispatchEvent]
      with GatewayEventBase.DispatchMixin {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): Dispatch = objWith(Dispatch, "op", newValue)

    @inline def d: GatewayDispatchEvent.UnknownGatewayDispatchEvent =
      selectDynamic[GatewayDispatchEvent.UnknownGatewayDispatchEvent]("d")

    @inline def withD(
        newValue: GatewayDispatchEvent.UnknownGatewayDispatchEvent
    ): Dispatch = objWith(Dispatch, "d", newValue)

    @inline def s: Int = selectDynamic[Int]("s")

    @inline def withS(newValue: Int): Dispatch = objWith(Dispatch, "s", newValue)

    @inline def t: GatewayDispatchType = selectDynamic[GatewayDispatchType]("t")

    @inline def withT(newValue: GatewayDispatchType): Dispatch = objWith(Dispatch, "t", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d, () => s, () => t)
  }
  object Dispatch extends DiscordObjectCompanion[Dispatch] with GatewayEventBase.GatewayEventCompanionMixin[Dispatch] {
    def makeRaw(json: Json, cache: Map[String, Any]): Dispatch = new Dispatch(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.Dispatch,
        d: GatewayDispatchEvent.UnknownGatewayDispatchEvent,
        s: Int,
        t: GatewayDispatchType
    ): Dispatch = makeRawFromFields("op" := op, "d" := d, "s" := s, "t" := t)

    override def op: GatewayEventOp = GatewayEventOp.Dispatch
  }

  class Heartbeat(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Option[Int]] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): Heartbeat = objWith(Heartbeat, "op", newValue)

    @inline def d: Option[Int] = selectDynamic[Option[Int]]("d")

    @inline def withD(newValue: Option[Int]): Heartbeat = objWith(Heartbeat, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object Heartbeat
      extends DiscordObjectCompanion[Heartbeat]
      with GatewayEventBase.GatewayEventCompanionMixin[Heartbeat] {
    def makeRaw(json: Json, cache: Map[String, Any]): Heartbeat = new Heartbeat(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.Heartbeat,
        d: Option[Int]
    ): Heartbeat = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Heartbeat
  }

  class Identify(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.Identify.Data] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): Identify = objWith(Identify, "op", newValue)

    @inline def d: GatewayEvent.Identify.Data = selectDynamic[GatewayEvent.Identify.Data]("d")

    @inline def withD(newValue: GatewayEvent.Identify.Data): Identify =
      objWith(Identify, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object Identify extends DiscordObjectCompanion[Identify] with GatewayEventBase.GatewayEventCompanionMixin[Identify] {
    def makeRaw(json: Json, cache: Map[String, Any]): Identify = new Identify(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.Identify,
        d: GatewayEvent.Identify.Data
    ): Identify = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Identify

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** The auth token */
      @inline def token: String = selectDynamic[String]("token")

      @inline def withToken(newValue: String): Data = objWith(Data, "token", newValue)

      /** Misc connection properties */
      @inline def properties: Map[String, String] = selectDynamic[Map[String, String]]("properties")

      @inline def withProperties(newValue: Map[String, String]): Data =
        objWith(Data, "properties", newValue)

      /** If the connection supports compression */
      @inline def compress: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("compress")

      @inline def withCompress(newValue: UndefOr[Boolean]): Data = objWithUndef(Data, "compress", newValue)

      /**
        * A number between 50 and 250 indicating when offline members should no
        * longer be sent.
        */
      @inline def largeThreshold: UndefOr[Int] = selectDynamic[UndefOr[Int]]("large_threshold")

      @inline def withLargeThreshold(newValue: UndefOr[Int]): Data = objWithUndef(Data, "large_threshold", newValue)

      /**
        * Array of two intergers, those being [shardId, numShards] indicating
        * how sharding should be done
        */
      @inline def shard: UndefOr[Seq[Int]] = selectDynamic[UndefOr[Seq[Int]]]("shard")

      @inline def withShard(newValue: UndefOr[Seq[Int]]): Data = objWithUndef(Data, "shard", newValue)

      /** Initial presence info */
      @inline def presence: UndefOr[GatewayEvent.UpdatePresence.Data] =
        selectDynamic[UndefOr[GatewayEvent.UpdatePresence.Data]]("presence")

      @inline def withPresence(newValue: UndefOr[GatewayEvent.UpdatePresence.Data]): Data =
        objWithUndef(Data, "presence", newValue)

      /** The gateway intents indicating what events we want to receive */
      @inline def intents: GatewayIntents = selectDynamic[GatewayIntents]("intents")

      @inline def withIntents(newValue: GatewayIntents): Data = objWith(Data, "intents", newValue)

      override def values: Seq[() => Any] = Seq(
        () => token,
        () => properties,
        () => compress,
        () => largeThreshold,
        () => shard,
        () => presence,
        () => intents
      )
    }
    object Data extends DiscordObjectCompanion[Data] {
      def makeRaw(json: Json, cache: Map[String, Any]): Data = new Data(json, cache)

      /**
        * @param token
        *   The auth token
        * @param properties
        *   Misc connection properties
        * @param compress
        *   If the connection supports compression
        * @param largeThreshold
        *   A number between 50 and 250 indicating when offline members should
        *   no longer be sent.
        * @param shard
        *   Array of two intergers, those being [shardId, numShards] indicating
        *   how sharding should be done
        * @param presence
        *   Initial presence info
        * @param intents
        *   The gateway intents indicating what events we want to receive
        */
      def make20(
          token: String,
          properties: Map[String, String],
          compress: UndefOr[Boolean] = UndefOrSome(false),
          largeThreshold: UndefOr[Int] = UndefOrUndefined(Some("largeThreshold")),
          shard: UndefOr[Seq[Int]] = UndefOrUndefined(Some("shard")),
          presence: UndefOr[GatewayEvent.UpdatePresence.Data] = UndefOrUndefined(Some("presence")),
          intents: GatewayIntents
      ): Data = makeRawFromFields(
        "token"            := token,
        "properties"       := properties,
        "compress"        :=? compress,
        "large_threshold" :=? largeThreshold,
        "shard"           :=? shard,
        "presence"        :=? presence,
        "intents"          := intents
      )
    }
  }

  class UpdatePresence(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.UpdatePresence.Data] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): UpdatePresence = objWith(UpdatePresence, "op", newValue)

    @inline def d: GatewayEvent.UpdatePresence.Data = selectDynamic[GatewayEvent.UpdatePresence.Data]("d")

    @inline def withD(newValue: GatewayEvent.UpdatePresence.Data): UpdatePresence =
      objWith(UpdatePresence, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object UpdatePresence
      extends DiscordObjectCompanion[UpdatePresence]
      with GatewayEventBase.GatewayEventCompanionMixin[UpdatePresence] {
    def makeRaw(json: Json, cache: Map[String, Any]): UpdatePresence =
      new UpdatePresence(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.UpdatePresence,
        d: GatewayEvent.UpdatePresence.Data
    ): UpdatePresence = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.UpdatePresence

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /**
        * Unix time (millis) of when client went idle, or null if client is not
        * idle
        */
      @inline def since: Option[Int] = selectDynamic[Option[Int]]("since")

      @inline def withSince(newValue: Option[Int]): Data = objWith(Data, "since", newValue)

      /** Client's activities */
      @inline def activities: Seq[GatewayDispatchEvent.Activity] =
        selectDynamic[Seq[GatewayDispatchEvent.Activity]]("activities")

      @inline def withActivities(newValue: Seq[GatewayDispatchEvent.Activity]): Data =
        objWith(Data, "activities", newValue)

      /** Client's status */
      @inline def status: Status = selectDynamic[Status]("status")

      @inline def withStatus(newValue: Status): Data = objWith(Data, "status", newValue)

      /** If the Client is AFK */
      @inline def afk: Boolean = selectDynamic[Boolean]("afk")

      @inline def withAfk(newValue: Boolean): Data = objWith(Data, "afk", newValue)

      override def values: Seq[() => Any] = Seq(() => since, () => activities, () => status, () => afk)
    }
    object Data extends DiscordObjectCompanion[Data] {
      def makeRaw(json: Json, cache: Map[String, Any]): Data = new Data(json, cache)

      /**
        * @param since
        *   Unix time (millis) of when client went idle, or null if client is
        *   not idle
        * @param activities
        *   Client's activities
        * @param status
        *   Client's status
        * @param afk
        *   If the Client is AFK
        */
      def make20(
          since: Option[Int],
          activities: Seq[GatewayDispatchEvent.Activity],
          status: Status,
          afk: Boolean
      ): Data = makeRawFromFields("since" := since, "activities" := activities, "status" := status, "afk" := afk)
    }
  }

  class UpdateVoiceState(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.UpdateVoiceState.Data] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): UpdateVoiceState = objWith(UpdateVoiceState, "op", newValue)

    @inline def d: GatewayEvent.UpdateVoiceState.Data = selectDynamic[GatewayEvent.UpdateVoiceState.Data]("d")

    @inline def withD(newValue: GatewayEvent.UpdateVoiceState.Data): UpdateVoiceState =
      objWith(UpdateVoiceState, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object UpdateVoiceState
      extends DiscordObjectCompanion[UpdateVoiceState]
      with GatewayEventBase.GatewayEventCompanionMixin[UpdateVoiceState] {
    def makeRaw(json: Json, cache: Map[String, Any]): UpdateVoiceState =
      new UpdateVoiceState(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.UpdateVoiceState,
        d: GatewayEvent.UpdateVoiceState.Data
    ): UpdateVoiceState = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.UpdateVoiceState

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** Id of the guild */
      @inline def guildId: GuildId = selectDynamic[GuildId]("guild_id")

      @inline def withGuildId(newValue: GuildId): Data = objWith(Data, "guild_id", newValue)

      /** Id of the voice channel to join, or null if disconnecting */
      @inline def channelId: Option[ChannelId] = selectDynamic[Option[ChannelId]]("channel_id")

      @inline def withChannelId(newValue: Option[ChannelId]): Data = objWith(Data, "channel_id", newValue)

      /** If the client is muted */
      @inline def selfMute: Boolean = selectDynamic[Boolean]("self_mute")

      @inline def withSelfMute(newValue: Boolean): Data = objWith(Data, "self_mute", newValue)

      /** If the client is deafened */
      @inline def selfDeaf: Boolean = selectDynamic[Boolean]("self_deaf")

      @inline def withSelfDeaf(newValue: Boolean): Data = objWith(Data, "self_deaf", newValue)

      override def values: Seq[() => Any] = Seq(() => guildId, () => channelId, () => selfMute, () => selfDeaf)
    }
    object Data extends DiscordObjectCompanion[Data] {
      def makeRaw(json: Json, cache: Map[String, Any]): Data = new Data(json, cache)

      /**
        * @param guildId
        *   Id of the guild
        * @param channelId
        *   Id of the voice channel to join, or null if disconnecting
        * @param selfMute
        *   If the client is muted
        * @param selfDeaf
        *   If the client is deafened
        */
      def make20(
          guildId: GuildId,
          channelId: Option[ChannelId],
          selfMute: Boolean,
          selfDeaf: Boolean
      ): Data = makeRawFromFields(
        "guild_id"   := guildId,
        "channel_id" := channelId,
        "self_mute"  := selfMute,
        "self_deaf"  := selfDeaf
      )
    }
  }

  class Resume(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.Resume.Data] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): Resume = objWith(Resume, "op", newValue)

    @inline def d: GatewayEvent.Resume.Data = selectDynamic[GatewayEvent.Resume.Data]("d")

    @inline def withD(newValue: GatewayEvent.Resume.Data): Resume = objWith(Resume, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object Resume extends DiscordObjectCompanion[Resume] with GatewayEventBase.GatewayEventCompanionMixin[Resume] {
    def makeRaw(json: Json, cache: Map[String, Any]): Resume = new Resume(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.Resume,
        d: GatewayEvent.Resume.Data
    ): Resume = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Resume

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** Session token */
      @inline def token: String = selectDynamic[String]("token")

      @inline def withToken(newValue: String): Data = objWith(Data, "token", newValue)

      /** Session id */
      @inline def sessionId: String = selectDynamic[String]("session_id")

      @inline def withSessionId(newValue: String): Data = objWith(Data, "session_id", newValue)

      /** Last sequence number received */
      @inline def seq: Int = selectDynamic[Int]("seq")

      @inline def withSeq(newValue: Int): Data = objWith(Data, "seq", newValue)

      override def values: Seq[() => Any] = Seq(() => token, () => sessionId, () => seq)
    }
    object Data extends DiscordObjectCompanion[Data] {
      def makeRaw(json: Json, cache: Map[String, Any]): Data = new Data(json, cache)

      /**
        * @param token
        *   Session token
        * @param sessionId
        *   Session id
        * @param seq
        *   Last sequence number received
        */
      def make20(token: String, sessionId: String, seq: Int): Data =
        makeRawFromFields("token" := token, "session_id" := sessionId, "seq" := seq)
    }
  }

  class Reconnect(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Unit]
      with GatewayEventBase.UnitMixin {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): Reconnect = objWith(Reconnect, "op", newValue)

    override def values: Seq[() => Any] = Seq(() => op)
  }
  object Reconnect
      extends DiscordObjectCompanion[Reconnect]
      with GatewayEventBase.GatewayEventCompanionMixin[Reconnect] {
    def makeRaw(json: Json, cache: Map[String, Any]): Reconnect = new Reconnect(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.Reconnect): Reconnect =
      makeRawFromFields("op" := op)

    override def op: GatewayEventOp = GatewayEventOp.Reconnect
  }

  class RequestGuildMembers(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.RequestGuildMembers.Data] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): RequestGuildMembers =
      objWith(RequestGuildMembers, "op", newValue)

    @inline def d: GatewayEvent.RequestGuildMembers.Data = selectDynamic[GatewayEvent.RequestGuildMembers.Data]("d")

    @inline def withD(newValue: GatewayEvent.RequestGuildMembers.Data): RequestGuildMembers =
      objWith(RequestGuildMembers, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object RequestGuildMembers
      extends DiscordObjectCompanion[RequestGuildMembers]
      with GatewayEventBase.GatewayEventCompanionMixin[RequestGuildMembers] {
    def makeRaw(json: Json, cache: Map[String, Any]): RequestGuildMembers =
      new RequestGuildMembers(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.RequestGuildMembers,
        d: GatewayEvent.RequestGuildMembers.Data
    ): RequestGuildMembers = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.RequestGuildMembers

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** Id of the guild to get members in */
      @inline def guildId: GuildId = selectDynamic[GuildId]("guild_id")

      @inline def withGuildId(newValue: GuildId): Data = objWith(Data, "guild_id", newValue)

      /** Query for usernames that begin with the string */
      @inline def query: UndefOr[String] = selectDynamic[UndefOr[String]]("query")

      @inline def withQuery(newValue: UndefOr[String]): Data = objWithUndef(Data, "query", newValue)

      /** Max amount of members to return */
      @inline def limit: Int = selectDynamic[Int]("limit")

      @inline def withLimit(newValue: Int): Data = objWith(Data, "limit", newValue)

      /** If presences should be included as well */
      @inline def presences: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("presences")

      @inline def withPresences(newValue: UndefOr[Boolean]): Data = objWithUndef(Data, "presences", newValue)

      /** Specific users to fetch */
      @inline def userIds: UndefOr[Seq[UserId]] = selectDynamic[UndefOr[Seq[UserId]]]("user_ids")

      @inline def withUserIds(newValue: UndefOr[Seq[UserId]]): Data = objWithUndef(Data, "user_ids", newValue)

      /** Nonce to identify chunk responses */
      @inline def nonce: UndefOr[String] = selectDynamic[UndefOr[String]]("nonce")

      @inline def withNonce(newValue: UndefOr[String]): Data = objWithUndef(Data, "nonce", newValue)

      override def values: Seq[() => Any] =
        Seq(() => guildId, () => query, () => limit, () => presences, () => userIds, () => nonce)
    }
    object Data extends DiscordObjectCompanion[Data] {
      def makeRaw(json: Json, cache: Map[String, Any]): Data = new Data(json, cache)

      /**
        * @param guildId
        *   Id of the guild to get members in
        * @param query
        *   Query for usernames that begin with the string
        * @param limit
        *   Max amount of members to return
        * @param presences
        *   If presences should be included as well
        * @param userIds
        *   Specific users to fetch
        * @param nonce
        *   Nonce to identify chunk responses
        */
      def make20(
          guildId: GuildId,
          query: UndefOr[String] = UndefOrUndefined(Some("query")),
          limit: Int,
          presences: UndefOr[Boolean] = UndefOrUndefined(Some("presences")),
          userIds: UndefOr[Seq[UserId]] = UndefOrUndefined(Some("userIds")),
          nonce: UndefOr[String] = UndefOrUndefined(Some("nonce"))
      ): Data = makeRawFromFields(
        "guild_id"   := guildId,
        "query"     :=? query,
        "limit"      := limit,
        "presences" :=? presences,
        "user_ids"  :=? userIds,
        "nonce"     :=? nonce
      )
    }
  }

  class InvalidSession(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Boolean] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): InvalidSession = objWith(InvalidSession, "op", newValue)

    @inline def d: Boolean = selectDynamic[Boolean]("d")

    @inline def withD(newValue: Boolean): InvalidSession = objWith(InvalidSession, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object InvalidSession
      extends DiscordObjectCompanion[InvalidSession]
      with GatewayEventBase.GatewayEventCompanionMixin[InvalidSession] {
    def makeRaw(json: Json, cache: Map[String, Any]): InvalidSession =
      new InvalidSession(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.InvalidSession,
        d: Boolean
    ): InvalidSession = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.InvalidSession
  }

  class Hello(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.Hello.Data] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): Hello = objWith(Hello, "op", newValue)

    @inline def d: GatewayEvent.Hello.Data = selectDynamic[GatewayEvent.Hello.Data]("d")

    @inline def withD(newValue: GatewayEvent.Hello.Data): Hello = objWith(Hello, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object Hello extends DiscordObjectCompanion[Hello] with GatewayEventBase.GatewayEventCompanionMixin[Hello] {
    def makeRaw(json: Json, cache: Map[String, Any]): Hello = new Hello(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.Hello,
        d: GatewayEvent.Hello.Data
    ): Hello = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Hello

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      @inline def heartbeatInterval: Int = selectDynamic[Int]("heartbeat_interval")

      @inline def withHeartbeatInterval(newValue: Int): Data = objWith(Data, "heartbeat_interval", newValue)

      override def values: Seq[() => Any] = Seq(() => heartbeatInterval)
    }
    object Data extends DiscordObjectCompanion[Data] {
      def makeRaw(json: Json, cache: Map[String, Any]): Data = new Data(json, cache)

      def make20(heartbeatInterval: Int): Data = makeRawFromFields("heartbeat_interval" := heartbeatInterval)
    }
  }

  class HeartbeatACK(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Unit]
      with GatewayEventBase.UnitMixin {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): HeartbeatACK = objWith(HeartbeatACK, "op", newValue)

    override def values: Seq[() => Any] = Seq(() => op)
  }
  object HeartbeatACK
      extends DiscordObjectCompanion[HeartbeatACK]
      with GatewayEventBase.GatewayEventCompanionMixin[HeartbeatACK] {
    def makeRaw(json: Json, cache: Map[String, Any]): HeartbeatACK = new HeartbeatACK(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.HeartbeatACK): HeartbeatACK =
      makeRawFromFields("op" := op)

    override def op: GatewayEventOp = GatewayEventOp.HeartbeatACK
  }

  class Unknown(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Json] {

    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def withOp(newValue: GatewayEventOp): Unknown = objWith(Unknown, "op", newValue)

    @inline def d: Json = selectDynamic[Json]("d")

    @inline def withD(newValue: Json): Unknown = objWith(Unknown, "d", newValue)

    override def values: Seq[() => Any] = Seq(() => op, () => d)
  }
  object Unknown extends DiscordObjectCompanion[Unknown] {
    def makeRaw(json: Json, cache: Map[String, Any]): Unknown = new Unknown(json, cache)

    def make20(op: GatewayEventOp, d: Json): Unknown = makeRawFromFields("op" := op, "d" := d)
  }
}
