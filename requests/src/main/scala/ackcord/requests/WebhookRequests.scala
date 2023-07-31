//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.requests

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/requests/WebhookRequests.yaml

import ackcord.data._
import ackcord.data.base._
import io.circe.Json
import sttp.model.Method

object WebhookRequests {

  class CreateWebhookBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** Name of the webhook (1-80 characters) */
    @inline def name: String = selectDynamic[String]("name")

    @inline def withName(newValue: String): CreateWebhookBody = objWith(CreateWebhookBody, "name", newValue)

    /** Image for the default webhook avatar */
    @inline def avatar: JsonOption[ImageData] = selectDynamic[JsonOption[ImageData]]("avatar")

    @inline def withAvatar(newValue: JsonOption[ImageData]): CreateWebhookBody =
      objWithUndef(CreateWebhookBody, "avatar", newValue)

    override def values: Seq[() => Any] = Seq(() => name, () => avatar)
  }
  object CreateWebhookBody extends DiscordObjectCompanion[CreateWebhookBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): CreateWebhookBody =
      new CreateWebhookBody(json, cache)

    /**
      * @param name
      *   Name of the webhook (1-80 characters)
      * @param avatar
      *   Image for the default webhook avatar
      */
    def make20(
        name: String,
        avatar: JsonOption[ImageData] = JsonUndefined(Some("avatar"))
    ): CreateWebhookBody = makeRawFromFields("name" := name, "avatar" :=? avatar)
  }

  /**
    * Creates a new webhook and returns a webhook object on success. Requires
    * the MANAGE_WEBHOOKS permission. Fires a Webhooks Update Gateway event.
    *
    * An error will be returned if a webhook name (name) is not valid. A webhook
    * name is valid if:
    *   - It does not contain the substrings clyde or discord (case-insensitive)
    *   - It follows the nickname guidelines in the Usernames and Nicknames
    *     documentation, with an exception that webhook names can be up to 80
    *     characters
    */
  def createWebhook(
      channelId: ChannelId,
      body: CreateWebhookBody,
      reason: Option[String]
  ): Request[CreateWebhookBody, Webhook] =
    Request.restRequest(
      route = (Route.Empty / "channels" / Parameters[ChannelId]("channelId", channelId, major = true) / "webhooks")
        .toRequest(Method.POST),
      params = body,
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  /**
    * Returns a list of channel webhook objects. Requires the MANAGE_WEBHOOKS
    * permission.
    */
  def getChannelWebhooks(channelId: ChannelId): Request[Unit, Seq[Webhook]] =
    Request.restRequest(
      route = (Route.Empty / "channels" / Parameters[ChannelId]("channelId", channelId, major = true) / "webhooks")
        .toRequest(Method.GET)
    )

  /**
    * Returns a list of guild webhook objects. Requires the MANAGE_WEBHOOKS
    * permission.
    */
  def getGuildWebhooks(guildId: GuildId): Request[Unit, Seq[Webhook]] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "webhooks")
        .toRequest(Method.GET)
    )

  /** Returns the new webhook object for the given id. */
  def getWebhook(webhookId: WebhookId): Request[Unit, Webhook] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true)).toRequest(Method.GET)
    )

  /**
    * Same as above, except this call does not require authentication and
    * returns no user in the webhook object.
    */
  def getWebhookwithToken(webhookId: WebhookId, webhookToken: String): Request[Unit, Webhook] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        )).toRequest(Method.GET)
    )

  class ModifyWebhookBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** The default name of the webhook */
    @inline def name: UndefOr[String] = selectDynamic[UndefOr[String]]("name")

    @inline def withName(newValue: UndefOr[String]): ModifyWebhookBody =
      objWithUndef(ModifyWebhookBody, "name", newValue)

    /** Image for the default webhook avatar */
    @inline def avatar: JsonOption[ImageData] = selectDynamic[JsonOption[ImageData]]("avatar")

    @inline def withAvatar(newValue: JsonOption[ImageData]): ModifyWebhookBody =
      objWithUndef(ModifyWebhookBody, "avatar", newValue)

    /** The new channel id this webhook should be moved to */
    @inline def channelId: UndefOr[TextGuildChannelId] = selectDynamic[UndefOr[TextGuildChannelId]]("channel_id")

    @inline def withChannelId(newValue: UndefOr[TextGuildChannelId]): ModifyWebhookBody =
      objWithUndef(ModifyWebhookBody, "channel_id", newValue)

    override def values: Seq[() => Any] = Seq(() => name, () => avatar, () => channelId)
  }
  object ModifyWebhookBody extends DiscordObjectCompanion[ModifyWebhookBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): ModifyWebhookBody =
      new ModifyWebhookBody(json, cache)

    /**
      * @param name
      *   The default name of the webhook
      * @param avatar
      *   Image for the default webhook avatar
      * @param channelId
      *   The new channel id this webhook should be moved to
      */
    def make20(
        name: UndefOr[String] = UndefOrUndefined(Some("name")),
        avatar: JsonOption[ImageData] = JsonUndefined(Some("avatar")),
        channelId: UndefOr[TextGuildChannelId] = UndefOrUndefined(Some("channel_id"))
    ): ModifyWebhookBody = makeRawFromFields("name" :=? name, "avatar" :=? avatar, "channel_id" :=? channelId)
  }

  /**
    * Modify a webhook. Requires the MANAGE_WEBHOOKS permission. Returns the
    * updated webhook object on success. Fires a Webhooks Update Gateway event.
    */
  def modifyWebhook(
      webhookId: WebhookId,
      body: ModifyWebhookBody,
      reason: Option[String]
  ): Request[ModifyWebhookBody, Webhook] =
    Request.restRequest(
      route = (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true))
        .toRequest(Method.PATCH),
      params = body,
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  class ModifyWebhookwithTokenBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** The default name of the webhook */
    @inline def name: UndefOr[String] = selectDynamic[UndefOr[String]]("name")

    @inline def withName(newValue: UndefOr[String]): ModifyWebhookwithTokenBody =
      objWithUndef(ModifyWebhookwithTokenBody, "name", newValue)

    /** Image for the default webhook avatar */
    @inline def avatar: JsonOption[ImageData] = selectDynamic[JsonOption[ImageData]]("avatar")

    @inline def withAvatar(newValue: JsonOption[ImageData]): ModifyWebhookwithTokenBody =
      objWithUndef(ModifyWebhookwithTokenBody, "avatar", newValue)

    override def values: Seq[() => Any] = Seq(() => name, () => avatar)
  }
  object ModifyWebhookwithTokenBody extends DiscordObjectCompanion[ModifyWebhookwithTokenBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): ModifyWebhookwithTokenBody =
      new ModifyWebhookwithTokenBody(json, cache)

    /**
      * @param name
      *   The default name of the webhook
      * @param avatar
      *   Image for the default webhook avatar
      */
    def make20(
        name: UndefOr[String] = UndefOrUndefined(Some("name")),
        avatar: JsonOption[ImageData] = JsonUndefined(Some("avatar"))
    ): ModifyWebhookwithTokenBody = makeRawFromFields("name" :=? name, "avatar" :=? avatar)
  }

  /**
    * Same as above, except this call does not require authentication, does not
    * accept a channel_id parameter in the body, and does not return a user in
    * the webhook object.
    */
  def modifyWebhookwithToken(
      webhookId: WebhookId,
      webhookToken: String,
      body: ModifyWebhookwithTokenBody,
      reason: Option[String]
  ): Request[ModifyWebhookwithTokenBody, Webhook] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        )).toRequest(Method.PATCH),
      params = body,
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  /**
    * Delete a webhook permanently. Requires the MANAGE_WEBHOOKS permission.
    * Returns a 204 No Content response on success. Fires a Webhooks Update
    * Gateway event.
    */
  def deleteWebhook(webhookId: WebhookId, reason: Option[String]): Request[Unit, Unit] =
    Request.restRequest(
      route = (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true))
        .toRequest(Method.DELETE),
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  /** Same as above, except this call does not require authentication. */
  def deleteWebhookwithToken(
      webhookId: WebhookId,
      webhookToken: String,
      reason: Option[String]
  ): Request[Unit, Unit] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        )).toRequest(Method.DELETE),
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  class ExecuteWebhookQuery(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /**
      * Waits for server confirmation of message send before response, and
      * returns the created message body (defaults to false; when false a
      * message that is not saved does not return an error)
      */
    @inline def doWait: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("wait")

    @inline def withDoWait(newValue: UndefOr[Boolean]): ExecuteWebhookQuery =
      objWithUndef(ExecuteWebhookQuery, "wait", newValue)

    /**
      * Send a message to the specified thread within a webhook's channel. The
      * thread will automatically be unarchived.
      */
    @inline def threadId: UndefOr[ThreadChannelId] = selectDynamic[UndefOr[ThreadChannelId]]("thread_id")

    @inline def withThreadId(newValue: UndefOr[ThreadChannelId]): ExecuteWebhookQuery =
      objWithUndef(ExecuteWebhookQuery, "thread_id", newValue)

    override def values: Seq[() => Any] = Seq(() => doWait, () => threadId)
  }
  object ExecuteWebhookQuery extends DiscordObjectCompanion[ExecuteWebhookQuery] {
    def makeRaw(json: Json, cache: Map[String, Any]): ExecuteWebhookQuery =
      new ExecuteWebhookQuery(json, cache)

    /**
      * @param doWait
      *   Waits for server confirmation of message send before response, and
      *   returns the created message body (defaults to false; when false a
      *   message that is not saved does not return an error)
      * @param threadId
      *   Send a message to the specified thread within a webhook's channel. The
      *   thread will automatically be unarchived.
      */
    def make20(
        doWait: UndefOr[Boolean] = UndefOrUndefined(Some("doWait")),
        threadId: UndefOr[ThreadChannelId] = UndefOrUndefined(Some("thread_id"))
    ): ExecuteWebhookQuery = makeRawFromFields("wait" :=? doWait, "thread_id" :=? threadId)
  }

  class ExecuteWebhookBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** Message contents (up to 2000 characters) */
    @inline def content: UndefOr[String] = selectDynamic[UndefOr[String]]("content")

    @inline def withContent(newValue: UndefOr[String]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "content", newValue)

    /** Override the default username of the webhook */
    @inline def username: UndefOr[String] = selectDynamic[UndefOr[String]]("username")

    @inline def withUsername(newValue: UndefOr[String]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "username", newValue)

    /** Override the default avatar of the webhook */
    @inline def avatarUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("avatar_url")

    @inline def withAvatarUrl(newValue: UndefOr[String]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "avatar_url", newValue)

    /** true if this is a TTS message */
    @inline def tts: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("tts")

    @inline def withTts(newValue: UndefOr[Boolean]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "tts", newValue)

    /** Up to 10 rich embeds (up to 6000 characters) */
    @inline def embeds: UndefOr[Seq[OutgoingEmbed]] = selectDynamic[UndefOr[Seq[OutgoingEmbed]]]("embeds")

    @inline def withEmbeds(newValue: UndefOr[Seq[OutgoingEmbed]]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "embeds", newValue)

    /** Allowed mentions for the message */
    @inline def allowedMentions: UndefOr[AllowedMentions] = selectDynamic[UndefOr[AllowedMentions]]("allowed_mentions")

    @inline def withAllowedMentions(newValue: UndefOr[AllowedMentions]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "allowed_mentions", newValue)

    /** Components to include with the message */
    @inline def components: UndefOr[Seq[Component]] = selectDynamic[UndefOr[Seq[Component]]]("components")

    @inline def withComponents(newValue: UndefOr[Seq[Component]]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "components", newValue)

    /** Attachment objects with filename and description */
    @inline def attachments: UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]] =
      selectDynamic[UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]]]("attachments")

    @inline def withAttachments(
        newValue: UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]]
    ): ExecuteWebhookBody = objWithUndef(ExecuteWebhookBody, "attachments", newValue)

    /**
      * Message flags combined as a bitfield (only SUPPRESS_EMBEDS and
      * SUPPRESS_NOTIFICATIONS can be set)
      */
    @inline def flags: UndefOr[Message.MessageFlags] = selectDynamic[UndefOr[Message.MessageFlags]]("flags")

    @inline def withFlags(newValue: UndefOr[Message.MessageFlags]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "flags", newValue)

    /**
      * Name of thread to create (requires the webhook channel to be a forum
      * channel)
      */
    @inline def threadName: UndefOr[String] = selectDynamic[UndefOr[String]]("thread_name")

    @inline def withThreadName(newValue: UndefOr[String]): ExecuteWebhookBody =
      objWithUndef(ExecuteWebhookBody, "thread_name", newValue)

    override def values: Seq[() => Any] = Seq(
      () => content,
      () => username,
      () => avatarUrl,
      () => tts,
      () => embeds,
      () => allowedMentions,
      () => components,
      () => attachments,
      () => flags,
      () => threadName
    )
  }
  object ExecuteWebhookBody
      extends DiscordObjectCompanion[ExecuteWebhookBody]
      with CreateMessageLikeMixin[ExecuteWebhookBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): ExecuteWebhookBody =
      new ExecuteWebhookBody(json, cache)

    /**
      * @param content
      *   Message contents (up to 2000 characters)
      * @param username
      *   Override the default username of the webhook
      * @param avatarUrl
      *   Override the default avatar of the webhook
      * @param tts
      *   true if this is a TTS message
      * @param embeds
      *   Up to 10 rich embeds (up to 6000 characters)
      * @param allowedMentions
      *   Allowed mentions for the message
      * @param components
      *   Components to include with the message
      * @param attachments
      *   Attachment objects with filename and description
      * @param flags
      *   Message flags combined as a bitfield (only SUPPRESS_EMBEDS and
      *   SUPPRESS_NOTIFICATIONS can be set)
      * @param threadName
      *   Name of thread to create (requires the webhook channel to be a forum
      *   channel)
      */
    def make20(
        content: UndefOr[String] = UndefOrUndefined(Some("content")),
        username: UndefOr[String] = UndefOrUndefined(Some("username")),
        avatarUrl: UndefOr[String] = UndefOrUndefined(Some("avatar_url")),
        tts: UndefOr[Boolean] = UndefOrUndefined(Some("tts")),
        embeds: UndefOr[Seq[OutgoingEmbed]] = UndefOrUndefined(Some("embeds")),
        allowedMentions: UndefOr[AllowedMentions] = UndefOrUndefined(Some("allowed_mentions")),
        components: UndefOr[Seq[Component]] = UndefOrUndefined(Some("components")),
        attachments: UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]] = UndefOrUndefined(Some("attachments")),
        flags: UndefOr[Message.MessageFlags] = UndefOrUndefined(Some("flags")),
        threadName: UndefOr[String] = UndefOrUndefined(Some("thread_name"))
    ): ExecuteWebhookBody = makeRawFromFields(
      "content"          :=? content,
      "username"         :=? username,
      "avatar_url"       :=? avatarUrl,
      "tts"              :=? tts,
      "embeds"           :=? embeds,
      "allowed_mentions" :=? allowedMentions,
      "components"       :=? components,
      "attachments"      :=? attachments,
      "flags"            :=? flags,
      "thread_name"      :=? threadName
    )
  }

  /**
    * Refer to Uploading Files for details on attachments and
    * multipart/form-data requests. Returns a message or 204 No Content
    * depending on the wait query parameter.
    */
  def executeWebhook[MPR](
      webhookId: WebhookId,
      webhookToken: String,
      query: ExecuteWebhookQuery = ExecuteWebhookQuery.make20(),
      body: ExecuteWebhookBody,
      parts: Seq[EncodeBody.Multipart[_, MPR]] = Nil
  ): ComplexRequest[ExecuteWebhookBody, Option[Message], MPR, Any] =
    Request.complexRestRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        ) +? Parameters.query("doWait", query.doWait) +? Parameters.query("thread_id", query.threadId))
          .toRequest(Method.POST),
      params = body,
      requestBody = Some(
        EncodeBody.MultipartBody(
          EncodeBody.Multipart.EncodeJson(body, "payload_json"),
          parts.zipWithIndex.map(t => t._1.withName(s"files[${t._2}]"))
        )
      ),
      parseResponse = Some(
        if (query.doWait.contains(true)) ParseResponse.AsJsonResponse[Message]().map(Some(_))
        else ParseResponse.ExpectNoBody.map(_ => None)
      )
    )

  class ExecuteSlackCompatibleWebhookQuery(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    /** Id of the thread to send the message in */
    @inline def threadId: UndefOr[ThreadChannelId] = selectDynamic[UndefOr[ThreadChannelId]]("thread_id")

    @inline def withThreadId(newValue: UndefOr[ThreadChannelId]): ExecuteSlackCompatibleWebhookQuery =
      objWithUndef(ExecuteSlackCompatibleWebhookQuery, "thread_id", newValue)

    /**
      * Waits for server confirmation of message send before response (defaults
      * to true; when false a message that is not saved does not return an
      * error)
      */
    @inline def doWait: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("wait")

    @inline def withDoWait(newValue: UndefOr[Boolean]): ExecuteSlackCompatibleWebhookQuery =
      objWithUndef(ExecuteSlackCompatibleWebhookQuery, "wait", newValue)

    override def values: Seq[() => Any] = Seq(() => threadId, () => doWait)
  }
  object ExecuteSlackCompatibleWebhookQuery extends DiscordObjectCompanion[ExecuteSlackCompatibleWebhookQuery] {
    def makeRaw(json: Json, cache: Map[String, Any]): ExecuteSlackCompatibleWebhookQuery =
      new ExecuteSlackCompatibleWebhookQuery(json, cache)

    /**
      * @param threadId
      *   Id of the thread to send the message in
      * @param doWait
      *   Waits for server confirmation of message send before response
      *   (defaults to true; when false a message that is not saved does not
      *   return an error)
      */
    def make20(
        threadId: UndefOr[ThreadChannelId] = UndefOrUndefined(Some("thread_id")),
        doWait: UndefOr[Boolean] = UndefOrUndefined(Some("doWait"))
    ): ExecuteSlackCompatibleWebhookQuery = makeRawFromFields("thread_id" :=? threadId, "wait" :=? doWait)
  }

  /**
    * Refer to Slack's documentation for more information. We do not support
    * Slack's channel, icon_emoji, mrkdwn, or mrkdwn_in properties.
    */
  def executeSlackCompatibleWebhook(
      webhookId: WebhookId,
      webhookToken: String,
      query: ExecuteSlackCompatibleWebhookQuery = ExecuteSlackCompatibleWebhookQuery.make20()
  ): Request[Unit, Option[Message]] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        ) / "slack" +? Parameters.query("thread_id", query.threadId) +? Parameters.query("doWait", query.doWait))
          .toRequest(Method.POST),
      parseResponse = Some(
        if (query.doWait.contains(true)) ParseResponse.AsJsonResponse[Message]().map(Some(_))
        else ParseResponse.ExpectNoBody.map(_ => None)
      )
    )

  class ExecuteGitHubCompatibleWebhookQuery(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    /** Id of the thread to send the message in */
    @inline def threadId: UndefOr[ThreadChannelId] = selectDynamic[UndefOr[ThreadChannelId]]("thread_id")

    @inline def withThreadId(newValue: UndefOr[ThreadChannelId]): ExecuteGitHubCompatibleWebhookQuery =
      objWithUndef(ExecuteGitHubCompatibleWebhookQuery, "thread_id", newValue)

    /**
      * Waits for server confirmation of message send before response (defaults
      * to true; when false a message that is not saved does not return an
      * error)
      */
    @inline def doWait: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("wait")

    @inline def withDoWait(newValue: UndefOr[Boolean]): ExecuteGitHubCompatibleWebhookQuery =
      objWithUndef(ExecuteGitHubCompatibleWebhookQuery, "wait", newValue)

    override def values: Seq[() => Any] = Seq(() => threadId, () => doWait)
  }
  object ExecuteGitHubCompatibleWebhookQuery extends DiscordObjectCompanion[ExecuteGitHubCompatibleWebhookQuery] {
    def makeRaw(json: Json, cache: Map[String, Any]): ExecuteGitHubCompatibleWebhookQuery =
      new ExecuteGitHubCompatibleWebhookQuery(json, cache)

    /**
      * @param threadId
      *   Id of the thread to send the message in
      * @param doWait
      *   Waits for server confirmation of message send before response
      *   (defaults to true; when false a message that is not saved does not
      *   return an error)
      */
    def make20(
        threadId: UndefOr[ThreadChannelId] = UndefOrUndefined(Some("thread_id")),
        doWait: UndefOr[Boolean] = UndefOrUndefined(Some("doWait"))
    ): ExecuteGitHubCompatibleWebhookQuery = makeRawFromFields("thread_id" :=? threadId, "wait" :=? doWait)
  }

  /**
    * Add a new webhook to your GitHub repo (in the repo's settings), and use
    * this endpoint as the "Payload URL." You can choose what events your
    * Discord channel receives by choosing the "Let me select individual events"
    * option and selecting individual events for the new webhook you're
    * configuring.
    */
  def executeGitHubCompatibleWebhook(
      webhookId: WebhookId,
      webhookToken: String,
      query: ExecuteGitHubCompatibleWebhookQuery = ExecuteGitHubCompatibleWebhookQuery.make20()
  ): Request[Unit, Option[Message]] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        ) / "github" +? Parameters.query("thread_id", query.threadId) +? Parameters.query("doWait", query.doWait))
          .toRequest(Method.POST),
      parseResponse = Some(
        if (query.doWait.contains(true)) ParseResponse.AsJsonResponse[Message]().map(Some(_))
        else ParseResponse.ExpectNoBody.map(_ => None)
      )
    )

  class GetWebhookMessageQuery(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** Id of the thread the message is in */
    @inline def threadId: UndefOr[ThreadChannelId] = selectDynamic[UndefOr[ThreadChannelId]]("thread_id")

    @inline def withThreadId(newValue: UndefOr[ThreadChannelId]): GetWebhookMessageQuery =
      objWithUndef(GetWebhookMessageQuery, "thread_id", newValue)

    override def values: Seq[() => Any] = Seq(() => threadId)
  }
  object GetWebhookMessageQuery extends DiscordObjectCompanion[GetWebhookMessageQuery] {
    def makeRaw(json: Json, cache: Map[String, Any]): GetWebhookMessageQuery =
      new GetWebhookMessageQuery(json, cache)

    /**
      * @param threadId
      *   Id of the thread the message is in
      */
    def make20(
        threadId: UndefOr[ThreadChannelId] = UndefOrUndefined(Some("thread_id"))
    ): GetWebhookMessageQuery = makeRawFromFields("thread_id" :=? threadId)
  }

  /**
    * Returns a previously-sent webhook message from the same token. Returns a
    * message object on success.
    */
  def getWebhookMessage(
      webhookId: WebhookId,
      webhookToken: String,
      messageId: MessageId,
      query: GetWebhookMessageQuery = GetWebhookMessageQuery.make20()
  ): Request[Unit, Message] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        ) / "messages" / Parameters[MessageId]("messageId", messageId) +? Parameters.query("thread_id", query.threadId))
          .toRequest(Method.GET)
    )

  class EditWebhookMessageQuery(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** Id of the thread the message is in */
    @inline def threadId: UndefOr[ThreadChannelId] = selectDynamic[UndefOr[ThreadChannelId]]("thread_id")

    @inline def withThreadId(newValue: UndefOr[ThreadChannelId]): EditWebhookMessageQuery =
      objWithUndef(EditWebhookMessageQuery, "thread_id", newValue)

    override def values: Seq[() => Any] = Seq(() => threadId)
  }
  object EditWebhookMessageQuery extends DiscordObjectCompanion[EditWebhookMessageQuery] {
    def makeRaw(json: Json, cache: Map[String, Any]): EditWebhookMessageQuery =
      new EditWebhookMessageQuery(json, cache)

    /**
      * @param threadId
      *   Id of the thread the message is in
      */
    def make20(
        threadId: UndefOr[ThreadChannelId] = UndefOrUndefined(Some("thread_id"))
    ): EditWebhookMessageQuery = makeRawFromFields("thread_id" :=? threadId)
  }

  class EditWebhookMessageBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** Message contents (up to 2000 characters) */
    @inline def content: UndefOr[String] = selectDynamic[UndefOr[String]]("content")

    @inline def withContent(newValue: UndefOr[String]): EditWebhookMessageBody =
      objWithUndef(EditWebhookMessageBody, "content", newValue)

    /** Up to 10 rich embeds (up to 6000 characters) */
    @inline def embeds: UndefOr[Seq[OutgoingEmbed]] = selectDynamic[UndefOr[Seq[OutgoingEmbed]]]("embeds")

    @inline def withEmbeds(newValue: UndefOr[Seq[OutgoingEmbed]]): EditWebhookMessageBody =
      objWithUndef(EditWebhookMessageBody, "embeds", newValue)

    /** Allowed mentions for the message */
    @inline def allowedMentions: UndefOr[AllowedMentions] = selectDynamic[UndefOr[AllowedMentions]]("allowed_mentions")

    @inline def withAllowedMentions(newValue: UndefOr[AllowedMentions]): EditWebhookMessageBody =
      objWithUndef(EditWebhookMessageBody, "allowed_mentions", newValue)

    /** Components to include with the message */
    @inline def components: UndefOr[Seq[Component]] = selectDynamic[UndefOr[Seq[Component]]]("components")

    @inline def withComponents(newValue: UndefOr[Seq[Component]]): EditWebhookMessageBody =
      objWithUndef(EditWebhookMessageBody, "components", newValue)

    /** Attachment objects with filename and description */
    @inline def attachments: UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]] =
      selectDynamic[UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]]]("attachments")

    @inline def withAttachments(
        newValue: UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]]
    ): EditWebhookMessageBody = objWithUndef(EditWebhookMessageBody, "attachments", newValue)

    override def values: Seq[() => Any] =
      Seq(() => content, () => embeds, () => allowedMentions, () => components, () => attachments)
  }
  object EditWebhookMessageBody
      extends DiscordObjectCompanion[EditWebhookMessageBody]
      with CreateMessageLikeMixin[EditWebhookMessageBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): EditWebhookMessageBody =
      new EditWebhookMessageBody(json, cache)

    /**
      * @param content
      *   Message contents (up to 2000 characters)
      * @param embeds
      *   Up to 10 rich embeds (up to 6000 characters)
      * @param allowedMentions
      *   Allowed mentions for the message
      * @param components
      *   Components to include with the message
      * @param attachments
      *   Attachment objects with filename and description
      */
    def make20(
        content: UndefOr[String] = UndefOrUndefined(Some("content")),
        embeds: UndefOr[Seq[OutgoingEmbed]] = UndefOrUndefined(Some("embeds")),
        allowedMentions: UndefOr[AllowedMentions] = UndefOrUndefined(Some("allowed_mentions")),
        components: UndefOr[Seq[Component]] = UndefOrUndefined(Some("components")),
        attachments: UndefOr[Seq[ChannelRequests.MessageCreateEditAttachment]] = UndefOrUndefined(Some("attachments"))
    ): EditWebhookMessageBody = makeRawFromFields(
      "content"          :=? content,
      "embeds"           :=? embeds,
      "allowed_mentions" :=? allowedMentions,
      "components"       :=? components,
      "attachments"      :=? attachments
    )
  }

  /**
    * Edits a previously-sent webhook message from the same token. Returns a
    * message object on success.
    *
    * When the content field is edited, the mentions array in the message object
    * will be reconstructed from scratch based on the new content. The
    * allowed_mentions field of the edit request controls how this happens. If
    * there is no explicit allowed_mentions in the edit request, the content
    * will be parsed with default allowances, that is, without regard to whether
    * or not an allowed_mentions was present in the request that originally
    * created the message.
    *
    * Refer to Uploading Files for details on attachments and
    * multipart/form-data requests. Any provided files will be appended to the
    * message. To remove or replace files you will have to supply the
    * attachments field which specifies the files to retain on the message after
    * edit.
    */
  def editWebhookMessage[MPR](
      webhookId: WebhookId,
      webhookToken: String,
      messageId: MessageId,
      query: EditWebhookMessageQuery = EditWebhookMessageQuery.make20(),
      body: EditWebhookMessageBody,
      parts: Seq[EncodeBody.Multipart[_, MPR]] = Nil
  ): ComplexRequest[EditWebhookMessageBody, Message, MPR, Any] =
    Request.complexRestRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        ) / "messages" / Parameters[MessageId]("messageId", messageId) +? Parameters.query("thread_id", query.threadId))
          .toRequest(Method.PATCH),
      params = body,
      requestBody = Some(
        EncodeBody.MultipartBody(
          EncodeBody.Multipart.EncodeJson(body, "payload_json"),
          parts.zipWithIndex.map(t => t._1.withName(s"files[${t._2}]"))
        )
      )
    )

  class DeleteWebhookMessageQuery(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** Id of the thread the message is in */
    @inline def threadId: UndefOr[ThreadChannelId] = selectDynamic[UndefOr[ThreadChannelId]]("thread_id")

    @inline def withThreadId(newValue: UndefOr[ThreadChannelId]): DeleteWebhookMessageQuery =
      objWithUndef(DeleteWebhookMessageQuery, "thread_id", newValue)

    override def values: Seq[() => Any] = Seq(() => threadId)
  }
  object DeleteWebhookMessageQuery extends DiscordObjectCompanion[DeleteWebhookMessageQuery] {
    def makeRaw(json: Json, cache: Map[String, Any]): DeleteWebhookMessageQuery =
      new DeleteWebhookMessageQuery(json, cache)

    /**
      * @param threadId
      *   Id of the thread the message is in
      */
    def make20(
        threadId: UndefOr[ThreadChannelId] = UndefOrUndefined(Some("thread_id"))
    ): DeleteWebhookMessageQuery = makeRawFromFields("thread_id" :=? threadId)
  }

  /**
    * Deletes a message that was created by the webhook. Returns a 204 No
    * Content response on success.
    */
  def deleteWebhookMessage(
      webhookId: WebhookId,
      webhookToken: String,
      messageId: MessageId,
      query: DeleteWebhookMessageQuery = DeleteWebhookMessageQuery.make20()
  ): Request[Unit, Unit] =
    Request.restRequest(
      route =
        (Route.Empty / "webhooks" / Parameters[WebhookId]("webhookId", webhookId, major = true) / Parameters[String](
          "webhookToken",
          webhookToken
        ) / "messages" / Parameters[MessageId]("messageId", messageId) +? Parameters.query("thread_id", query.threadId))
          .toRequest(Method.DELETE)
    )
}
