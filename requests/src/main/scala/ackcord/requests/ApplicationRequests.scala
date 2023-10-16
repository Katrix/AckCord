//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.requests

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/requests/ApplicationRequests.yaml

import ackcord.data._
import ackcord.data.base._
import io.circe.Json
import sttp.model.Method

object ApplicationRequests {

  val getCurrentApplication: Request[Unit, Application] =
    Request.restRequest(
      route = (Route.Empty / "applications" / "@me").toRequest(Method.GET)
    )

  class EditCurrentApplicationBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** Default custom authorization URL for the app, if enabled */
    @inline def customInstallUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("custom_install_url")

    @inline def withCustomInstallUrl(newValue: UndefOr[String]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "custom_install_url", newValue)

    /** Description of the app */
    @inline def description: UndefOr[String] = selectDynamic[UndefOr[String]]("description")

    @inline def withDescription(newValue: UndefOr[String]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "description", newValue)

    /** Role connection verification URL for the app */
    @inline def roleConnectionsVerificationUrl: UndefOr[String] =
      selectDynamic[UndefOr[String]]("role_connections_verification_url")

    @inline def withRoleConnectionsVerificationUrl(newValue: UndefOr[String]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "role_connections_verification_url", newValue)

    /** Settings for the app's default in-app authorization link, if enabled */
    @inline def installParams: UndefOr[Application.InstallParams] =
      selectDynamic[UndefOr[Application.InstallParams]]("install_params")

    @inline def withInstallParams(newValue: UndefOr[Application.InstallParams]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "install_params", newValue)

    /**
      * App's public flags Only limited intent flags (GATEWAY_PRESENCE_LIMITED,
      * GATEWAY_GUILD_MEMBERS_LIMITED, and GATEWAY_MESSAGE_CONTENT_LIMITED) can
      * be updated via the API.
      */
    @inline def flags: UndefOr[Application.Flags] = selectDynamic[UndefOr[Application.Flags]]("flags")

    @inline def withFlags(newValue: UndefOr[Application.Flags]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "flags", newValue)

    /** Icon for the app */
    @inline def icon: JsonOption[ImageData] = selectDynamic[JsonOption[ImageData]]("icon")

    @inline def withIcon(newValue: JsonOption[ImageData]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "icon", newValue)

    /** Default rich presence invite cover image for the app */
    @inline def coverImage: JsonOption[ImageData] = selectDynamic[JsonOption[ImageData]]("cover_image")

    @inline def withCoverImage(newValue: JsonOption[ImageData]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "cover_image", newValue)

    /**
      * Interactions endpoint URL for the app To update an Interactions endpoint
      * URL via the API, the URL must be valid according to the Receiving an
      * Interaction documentation.
      */
    @inline def interactionsEndpointUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("interactions_endpoint_url")

    @inline def withInteractionsEndpointUrl(newValue: UndefOr[String]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "interactions_endpoint_url", newValue)

    /**
      * List of tags describing the content and functionality of the app (max of
      * 20 characters per tag). Max of 5 tags.
      */
    @inline def tags: UndefOr[Seq[String]] = selectDynamic[UndefOr[Seq[String]]]("tags")

    @inline def withTags(newValue: UndefOr[Seq[String]]): EditCurrentApplicationBody =
      objWithUndef(EditCurrentApplicationBody, "tags", newValue)

    override def values: Seq[() => Any] = Seq(
      () => customInstallUrl,
      () => description,
      () => roleConnectionsVerificationUrl,
      () => installParams,
      () => flags,
      () => icon,
      () => coverImage,
      () => interactionsEndpointUrl,
      () => tags
    )
  }
  object EditCurrentApplicationBody extends DiscordObjectCompanion[EditCurrentApplicationBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): EditCurrentApplicationBody =
      new EditCurrentApplicationBody(json, cache)

    /**
      * @param customInstallUrl
      *   Default custom authorization URL for the app, if enabled
      * @param description
      *   Description of the app
      * @param roleConnectionsVerificationUrl
      *   Role connection verification URL for the app
      * @param installParams
      *   Settings for the app's default in-app authorization link, if enabled
      * @param flags
      *   App's public flags Only limited intent flags
      *   (GATEWAY_PRESENCE_LIMITED, GATEWAY_GUILD_MEMBERS_LIMITED, and
      *   GATEWAY_MESSAGE_CONTENT_LIMITED) can be updated via the API.
      * @param icon
      *   Icon for the app
      * @param coverImage
      *   Default rich presence invite cover image for the app
      * @param interactionsEndpointUrl
      *   Interactions endpoint URL for the app To update an Interactions
      *   endpoint URL via the API, the URL must be valid according to the
      *   Receiving an Interaction documentation.
      * @param tags
      *   List of tags describing the content and functionality of the app (max
      *   of 20 characters per tag). Max of 5 tags.
      */
    def make20(
        customInstallUrl: UndefOr[String] = UndefOrUndefined(Some("custom_install_url")),
        description: UndefOr[String] = UndefOrUndefined(Some("description")),
        roleConnectionsVerificationUrl: UndefOr[String] = UndefOrUndefined(Some("role_connections_verification_url")),
        installParams: UndefOr[Application.InstallParams] = UndefOrUndefined(Some("install_params")),
        flags: UndefOr[Application.Flags] = UndefOrUndefined(Some("flags")),
        icon: JsonOption[ImageData] = JsonUndefined(Some("icon")),
        coverImage: JsonOption[ImageData] = JsonUndefined(Some("cover_image")),
        interactionsEndpointUrl: UndefOr[String] = UndefOrUndefined(Some("interactions_endpoint_url")),
        tags: UndefOr[Seq[String]] = UndefOrUndefined(Some("tags"))
    ): EditCurrentApplicationBody = makeRawFromFields(
      "custom_install_url"                :=? customInstallUrl,
      "description"                       :=? description,
      "role_connections_verification_url" :=? roleConnectionsVerificationUrl,
      "install_params"                    :=? installParams,
      "flags"                             :=? flags,
      "icon"                              :=? icon,
      "cover_image"                       :=? coverImage,
      "interactions_endpoint_url"         :=? interactionsEndpointUrl,
      "tags"                              :=? tags
    )
  }

  def editCurrentApplication(body: EditCurrentApplicationBody): Request[EditCurrentApplicationBody, Application] =
    Request.restRequest(
      route = (Route.Empty / "applications" / "@me").toRequest(Method.PATCH),
      params = body
    )
}
