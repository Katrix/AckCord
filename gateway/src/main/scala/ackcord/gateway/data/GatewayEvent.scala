package ackcord.gateway.data

import ackcord.data.UndefOr
import ackcord.data.UndefOrSome
import ackcord.data.GuildId
import ackcord.data.ChannelId
import ackcord.data.UserId
import ackcord.data.base.{DiscordObject, DiscordObjectCompanion, DiscordStringEnum, DiscordStringEnumCompanion}
import io.circe.Json

object GatewayEvent extends GatewayEventBase.TopMixin {

  class Dispatch(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Json] {
    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def d: Json = selectDynamic[Json]("d")

    @inline def s: Int = selectDynamic[Int]("s")

    @inline def t: GatewayDispatchType = selectDynamic[GatewayDispatchType]("t")
  }
  object Dispatch extends DiscordObjectCompanion[Dispatch] with GatewayEventBase.GatewayEventCompanionMixin[Dispatch] {
    def makeRaw(json: Json, cache: Map[String, Any]): Dispatch = new Dispatch(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.Dispatch, d: Json, s: Int, t: GatewayDispatchType): Dispatch =
      makeRawFromFields("op" := op, "d" := d, "s" := s, "t" := t)

    override def op: GatewayEventOp = GatewayEventOp.Dispatch
  }

  class Heartbeat(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Option[Int]] {
    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def d: Option[Int] = selectDynamic[Option[Int]]("d")
  }
  object Heartbeat
      extends DiscordObjectCompanion[Heartbeat]
      with GatewayEventBase.GatewayEventCompanionMixin[Heartbeat] {
    def makeRaw(json: Json, cache: Map[String, Any]): Heartbeat = new Heartbeat(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.Heartbeat, d: Option[Int]): Heartbeat =
      makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Heartbeat
  }

  class Identify(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.Identify.Data] {
    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def d: GatewayEvent.Identify.Data = selectDynamic[GatewayEvent.Identify.Data]("d")
  }
  object Identify extends DiscordObjectCompanion[Identify] with GatewayEventBase.GatewayEventCompanionMixin[Identify] {
    def makeRaw(json: Json, cache: Map[String, Any]): Identify = new Identify(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.Identify, d: GatewayEvent.Identify.Data): Identify =
      makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Identify

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** The auth token */
      @inline def token: String = selectDynamic[String]("token")

      /** Misc connection properties */
      @inline def properties: Map[String, String] = selectDynamic[Map[String, String]]("properties")

      /** If the connection supports compression */
      @inline def compress: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("compress")

      /**
        * A number between 50 and 250 indicating when offline members should no
        * longer be sent.
        */
      @inline def largeThreshold: UndefOr[Int] = selectDynamic[UndefOr[Int]]("large_threshold")

      /**
        * Array of two intergers, those being [shardId, numShards] indicating
        * how sharding should be done
        */
      @inline def shard: UndefOr[Seq[Int]] = selectDynamic[UndefOr[Seq[Int]]]("shard")

      /** Initial presence info */
      @inline def presence: UndefOr[GatewayEvent.UpdatePresence.Data] =
        selectDynamic[UndefOr[GatewayEvent.UpdatePresence.Data]]("presence")

      /** The gateway intents indicating what events we want to receive */
      @inline def intents: GatewayIntents = selectDynamic[GatewayIntents]("intents")
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
          largeThreshold: UndefOr[Int],
          shard: UndefOr[Seq[Int]],
          presence: UndefOr[GatewayEvent.UpdatePresence.Data],
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

    @inline def d: GatewayEvent.UpdatePresence.Data = selectDynamic[GatewayEvent.UpdatePresence.Data]("d")
  }
  object UpdatePresence
      extends DiscordObjectCompanion[UpdatePresence]
      with GatewayEventBase.GatewayEventCompanionMixin[UpdatePresence] {
    def makeRaw(json: Json, cache: Map[String, Any]): UpdatePresence = new UpdatePresence(json, cache)

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

      /** Client's activities */
      @inline def activities: Seq[Activity] = selectDynamic[Seq[Activity]]("activities")

      /** Client's status */
      @inline def status: GatewayEvent.UpdatePresence.Status =
        selectDynamic[GatewayEvent.UpdatePresence.Status]("status")

      /** If the Client is AFK */
      @inline def afk: Boolean = selectDynamic[Boolean]("afk")
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
          activities: Seq[Activity],
          status: GatewayEvent.UpdatePresence.Status,
          afk: Boolean
      ): Data = makeRawFromFields("since" := since, "activities" := activities, "status" := status, "afk" := afk)

    }

    sealed case class Status private (value: String) extends DiscordStringEnum
    object Status extends DiscordStringEnumCompanion[Status] {

      val Online: Status       = Status("online")
      val DoNotDisturb: Status = Status("dnd")
      val Idle: Status         = Status("idle")
      val Invisible: Status    = Status("invisible")
      val Offline: Status      = Status("Offline")

      def unknown(value: String): Status = new Status(value)

      def values: Seq[Status] = Seq(Online, DoNotDisturb, Idle, Invisible, Offline)

    }
  }

  class UpdateVoiceState(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.UpdateVoiceState.Data] {
    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def d: GatewayEvent.UpdateVoiceState.Data = selectDynamic[GatewayEvent.UpdateVoiceState.Data]("d")
  }
  object UpdateVoiceState
      extends DiscordObjectCompanion[UpdateVoiceState]
      with GatewayEventBase.GatewayEventCompanionMixin[UpdateVoiceState] {
    def makeRaw(json: Json, cache: Map[String, Any]): UpdateVoiceState = new UpdateVoiceState(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.UpdateVoiceState,
        d: GatewayEvent.UpdateVoiceState.Data
    ): UpdateVoiceState = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.UpdateVoiceState

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** Id of the guild */
      @inline def guildId: GuildId = selectDynamic[GuildId]("guild_id")

      /** Id of the voice channel to join, or null if disconnecting */
      @inline def channelId: Option[ChannelId] = selectDynamic[Option[ChannelId]]("channel_id")

      /** If the client is muted */
      @inline def selfMute: Boolean = selectDynamic[Boolean]("self_mute")

      /** If the client is deafened */
      @inline def selfDeaf: Boolean = selectDynamic[Boolean]("self_deaf")
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
      def make20(guildId: GuildId, channelId: Option[ChannelId], selfMute: Boolean, selfDeaf: Boolean): Data =
        makeRawFromFields(
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

    @inline def d: GatewayEvent.Resume.Data = selectDynamic[GatewayEvent.Resume.Data]("d")
  }
  object Resume extends DiscordObjectCompanion[Resume] with GatewayEventBase.GatewayEventCompanionMixin[Resume] {
    def makeRaw(json: Json, cache: Map[String, Any]): Resume = new Resume(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.Resume, d: GatewayEvent.Resume.Data): Resume =
      makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Resume

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** Session token */
      @inline def token: String = selectDynamic[String]("token")

      /** Session id */
      @inline def sessionId: String = selectDynamic[String]("session_id")

      /** Last sequence number received */
      @inline def seq: Int = selectDynamic[Int]("seq")
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
  }
  object Reconnect
      extends DiscordObjectCompanion[Reconnect]
      with GatewayEventBase.GatewayEventCompanionMixin[Reconnect] {
    def makeRaw(json: Json, cache: Map[String, Any]): Reconnect = new Reconnect(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.Reconnect): Reconnect = makeRawFromFields("op" := op)

    override def op: GatewayEventOp = GatewayEventOp.Reconnect
  }

  class RequestGuildMembers(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.RequestGuildMembers.Data] {
    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def d: GatewayEvent.RequestGuildMembers.Data = selectDynamic[GatewayEvent.RequestGuildMembers.Data]("d")
  }
  object RequestGuildMembers
      extends DiscordObjectCompanion[RequestGuildMembers]
      with GatewayEventBase.GatewayEventCompanionMixin[RequestGuildMembers] {
    def makeRaw(json: Json, cache: Map[String, Any]): RequestGuildMembers = new RequestGuildMembers(json, cache)

    def make20(
        op: GatewayEventOp = GatewayEventOp.RequestGuildMembers,
        d: GatewayEvent.RequestGuildMembers.Data
    ): RequestGuildMembers = makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.RequestGuildMembers

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** Id of the guild to get members in */
      @inline def guildId: GuildId = selectDynamic[GuildId]("guild_id")

      /** Query for usernames that begin with the string */
      @inline def query: UndefOr[String] = selectDynamic[UndefOr[String]]("query")

      /** Max amount of members to return */
      @inline def limit: Int = selectDynamic[Int]("limit")

      /** If presences should be included as well */
      @inline def presences: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("presences")

      /** Specific users to fetch */
      @inline def userIds: UndefOr[Seq[UserId]] = selectDynamic[UndefOr[Seq[UserId]]]("user_ids")

      /** Nonce to identify chunk responses */
      @inline def nonce: UndefOr[String] = selectDynamic[UndefOr[String]]("nonce")
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
          query: UndefOr[String],
          limit: Int,
          presences: UndefOr[Boolean],
          userIds: UndefOr[Seq[UserId]],
          nonce: UndefOr[String]
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

    @inline def d: Boolean = selectDynamic[Boolean]("d")
  }
  object InvalidSession
      extends DiscordObjectCompanion[InvalidSession]
      with GatewayEventBase.GatewayEventCompanionMixin[InvalidSession] {
    def makeRaw(json: Json, cache: Map[String, Any]): InvalidSession = new InvalidSession(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.InvalidSession, d: Boolean): InvalidSession =
      makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.InvalidSession
  }

  class Hello(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[GatewayEvent.Hello.Data] {
    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def d: GatewayEvent.Hello.Data = selectDynamic[GatewayEvent.Hello.Data]("d")
  }
  object Hello extends DiscordObjectCompanion[Hello] with GatewayEventBase.GatewayEventCompanionMixin[Hello] {
    def makeRaw(json: Json, cache: Map[String, Any]): Hello = new Hello(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.Hello, d: GatewayEvent.Hello.Data): Hello =
      makeRawFromFields("op" := op, "d" := d)

    override def op: GatewayEventOp = GatewayEventOp.Hello

    class Data(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {
      @inline def heartbeatInterval: Int = selectDynamic[Int]("heartbeat_interval")
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
  }
  object HeartbeatACK
      extends DiscordObjectCompanion[HeartbeatACK]
      with GatewayEventBase.GatewayEventCompanionMixin[HeartbeatACK] {
    def makeRaw(json: Json, cache: Map[String, Any]): HeartbeatACK = new HeartbeatACK(json, cache)

    def make20(op: GatewayEventOp = GatewayEventOp.HeartbeatACK): HeartbeatACK = makeRawFromFields("op" := op)

    override def op: GatewayEventOp = GatewayEventOp.HeartbeatACK
  }

  class Unknown(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with GatewayEventBase[Json] {
    @inline def op: GatewayEventOp = selectDynamic[GatewayEventOp]("op")

    @inline def d: Json = selectDynamic[Json]("d")
  }
  object Unknown extends DiscordObjectCompanion[Unknown] {
    def makeRaw(json: Json, cache: Map[String, Any]): Unknown = new Unknown(json, cache)

    def make20(op: GatewayEventOp, d: Json): Unknown = makeRawFromFields("op" := op, "d" := d)

  }
}
