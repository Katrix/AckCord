//noinspection ScalaUnusedSymbol
package ackcord.interactions

import java.util.UUID

import ackcord.data.Component._
import ackcord.data._
import ackcord.gateway.Context
import ackcord.requests.Requests
import cats.MonadError
import cats.effect.kernel.Sync
import cats.effect.std.MapRef
import cats.syntax.all._

trait Components[F[_]] extends InteractionsProcess[F] {
  import Components._

  def makeRandomCustomId: F[String]

  def registerRawHandler(customId: String)(handler: (Interaction, Context) => F[HighInteractionResponse[F]]): F[Unit]

  def buttons: ButtonComponents[F]
  def textInputs: TextInputComponents[F]
  def stringSelects: StringSelectMenuComponents[F]
  def userSelects: UserSelectMenuComponents[F]
  def roleSelects: RoleSelectMenuComponents[F]
  def mentionableSelects: MentionableSelectMenuComponents[F]
  def channelSelects: ChannelSelectMenuComponents[F]
}
object Components {

  abstract class Base[F[_]](implicit F: MonadError[F, Throwable]) extends Components[F] {

    override val buttons: ButtonComponents[F]                 = new ButtonComponents.Default[F](this)
    override val textInputs: TextInputComponents[F]           = new TextInputComponents.Default[F](this)
    override val stringSelects: StringSelectMenuComponents[F] = new StringSelectMenuComponents.Default[F](this)
    override val userSelects: UserSelectMenuComponents[F]     = new UserSelectMenuComponents.Default[F](this)
    override val roleSelects: RoleSelectMenuComponents[F]     = new RoleSelectMenuComponents.Default[F](this)
    override val mentionableSelects: MentionableSelectMenuComponents[F] =
      new MentionableSelectMenuComponents.Default[F](this)
    override val channelSelects: ChannelSelectMenuComponents[F] = new ChannelSelectMenuComponents.Default[F](this)
  }

  class CatsInteractions[F[_]](
      val requests: Requests[F, Any],
      val respondToPing: Boolean,
      handlers: MapRef[F, String, Option[(Interaction, Context) => F[HighInteractionResponse[F]]]]
  )(
      implicit override val F: Sync[F]
  ) extends Base[F] {
    override def name: String = "CatsInteractions"

    override def makeRandomCustomId: F[String] = F.delay(UUID.randomUUID().toString)

    override def registerRawHandler(customId: String)(
        handler: (Interaction, Context) => F[HighInteractionResponse[F]]
    ): F[Unit] =
      handlers(customId).set(Some(handler))

    override def processComponentInteraction(
        interaction: Interaction,
        context: Context
    ): F[Option[HighInteractionResponse[F]]] = {
      interaction.data match {
        case UndefOrSome(data: Interaction.MessageComponentData) =>
          handlers(data.customId).get.flatMap(_.traverse(f => f(interaction, context)))
        case _ => F.pure(None)
      }
    }
  }

  def ofCats[F[_]](requests: Requests[F, Any], respondToPing: Boolean)(implicit F: Sync[F]): F[CatsInteractions[F]] =
    MapRef
      .ofConcurrentHashMap[F, String, (Interaction, Context) => F[HighInteractionResponse[F]]]()
      .map(new CatsInteractions[F](requests, respondToPing, _))

  abstract class SpecificComponent[F[_], C <: Component, CD](implicit F: MonadError[F, Throwable]) {
    def top: Components[F]

    def tpe: ComponentType

    def extractData(interaction: Interaction, customId: String): F[CD]

    def registerHandler(customId: String)(handler: ComponentInvocation[CD] => F[HighInteractionResponse[F]]): F[Unit] =
      top.registerRawHandler(customId) { (interaction, context) =>
        extractData(interaction, customId).flatMap(cd =>
          handler(
            ComponentInvocation(interaction, interaction.data.get.retype(Interaction.MessageComponentData), cd, context)
          )
        )
      }
  }
  object SpecificComponent {
    def makeBase[F[_], C <: Component, CD](
        interactions: SpecificComponent[F, C, CD],
        handler: ComponentInvocation[CD] => F[HighInteractionResponse[F]]
    )(make: String => C)(implicit F: MonadError[F, Throwable]): F[C] =
      interactions.top.makeRandomCustomId
        .flatTap(interactions.registerHandler(_)(handler))
        .map(make)
  }

  trait ButtonComponents[F[_]] extends SpecificComponent[F, Button, Unit] {
    override def tpe: ComponentType = ComponentType.Button

    def make(
        label: Option[String] = None,
        emoji: Option[ComponentEmoji] = None,
        style: Button.ButtonStyle = Button.ButtonStyle.Primary,
        disabled: Boolean = false
    )(handler: ComponentInvocation[Unit] => F[HighInteractionResponse[F]]): F[Button]

    def makeLink(
        url: String,
        label: Option[String] = None,
        emoji: Option[ComponentEmoji] = None,
        disabled: Boolean = false
    ): Button =
      Button.make20(
        tpe = tpe,
        style = Button.ButtonStyle.Link,
        label = UndefOr.fromOption(label),
        emoji = UndefOr.fromOption(emoji),
        url = UndefOrSome(url),
        disabled = UndefOr.someIfTrue(disabled)
      )
  }
  object ButtonComponents {
    class Default[F[_]](val top: Components[F])(implicit F: MonadError[F, Throwable]) extends ButtonComponents[F] {
      override def make(
          label: Option[String],
          emoji: Option[ComponentEmoji],
          style: Button.ButtonStyle,
          disabled: Boolean
      )(handler: ComponentInvocation[Unit] => F[HighInteractionResponse[F]]): F[Button] =
        SpecificComponent.makeBase(this, handler) { customId =>
          Button.make20(
            customId = UndefOrSome(customId),
            label = UndefOr.fromOption(label),
            emoji = UndefOr.fromOption(emoji),
            style = style,
            disabled = UndefOr.someIfTrue(disabled)
          )
        }

      override def extractData(interaction: Interaction, customId: String): F[Unit] = ().pure
    }
  }

  trait TextInputComponents[F[_]] extends SpecificComponent[F, TextInput, String] {
    override def tpe: ComponentType = ComponentType.TextInput

    def make(
        label: String,
        style: TextInput.TextInputStyle = TextInput.TextInputStyle.Short,
        minLength: Option[Int] = None,
        maxLength: Option[Int] = None,
        required: Boolean = false,
        value: Option[String] = None,
        placeholder: Option[String] = None
    )(handler: ComponentInvocation[String] => F[HighInteractionResponse[F]]): F[TextInput]
  }
  object TextInputComponents {
    class Default[F[_]](val top: Components[F])(implicit F: MonadError[F, Throwable]) extends TextInputComponents[F] {
      override def make(
          label: String,
          style: TextInput.TextInputStyle,
          minLength: Option[Int],
          maxLength: Option[Int],
          required: Boolean,
          value: Option[String],
          placeholder: Option[String]
      )(handler: ComponentInvocation[String] => F[HighInteractionResponse[F]]): F[TextInput] =
        SpecificComponent.makeBase(this, handler) { customId =>
          TextInput.make20(
            customId = customId,
            label = label,
            style = style,
            minLength = UndefOr.fromOption(minLength),
            maxLength = UndefOr.fromOption(maxLength),
            required = UndefOr.someIfTrue(required),
            value = UndefOr.fromOption(value),
            placeholder = UndefOr.fromOption(placeholder)
          )
        }

      override def extractData(interaction: Interaction, customId: String): F[String] =
        interaction.data match {
          case UndefOrSome(value: Interaction.ModalSubmitData) =>
            value.components
              .collectFirst {
                case com: Component.TextInput if com.customId == customId =>
                  com.value.toEither.pure.rethrow
              }
              .getOrElse(
                F.raiseError(MissingFieldException.messageAndData(s"Could not find component with id $customId", value))
              )

          case _ =>
            F.raiseError(
              MissingFieldException.messageAndData("Interaction data missing or with wrong type", interaction)
            )
        }
    }
  }

  trait StringSelectMenuComponents[F[_]] extends SpecificComponent[F, SelectMenu, Seq[String]] {
    override def tpe: ComponentType = ComponentType.StringSelect

    def make(
        options: Seq[SelectOption],
        placeholder: Option[String] = None,
        minValues: Int = 1,
        maxValues: Int = 1,
        disabled: Boolean = false
    )(handler: ComponentInvocation[Seq[String]] => F[HighInteractionResponse[F]]): F[SelectMenu]
  }
  object StringSelectMenuComponents {
    class Default[F[_]](val top: Components[F])(implicit F: MonadError[F, Throwable])
        extends StringSelectMenuComponents[F] {
      override def make(
          options: Seq[SelectOption],
          placeholder: Option[String],
          minValues: Int,
          maxValues: Int,
          disabled: Boolean
      )(handler: ComponentInvocation[Seq[String]] => F[HighInteractionResponse[F]]): F[SelectMenu] =
        SpecificComponent.makeBase(this, handler) { customId =>
          SelectMenu.make20(
            tpe = ComponentType.StringSelect,
            customId = customId,
            options = UndefOrSome(options),
            placeholder = UndefOr.fromOption(placeholder),
            minValues = if (minValues != 1) UndefOrSome(minValues) else UndefOrUndefined(),
            maxValues = if (maxValues != 1) UndefOrSome(maxValues) else UndefOrUndefined(),
            disabled = UndefOr.someIfTrue(disabled)
          )
        }

      override def extractData(interaction: Interaction, customId: String): F[Seq[String]] =
        interaction.data match {
          case UndefOrSome(value: Interaction.MessageComponentData) =>
            value.selectValues.getOrElse(Nil).map(_.value).pure

          case _ =>
            F.raiseError(
              MissingFieldException.messageAndData("Interaction data missing or with wrong type", interaction)
            )
        }
    }
  }

  private def interactionValuesResolved(
      data: Interaction.MessageComponentData
  ): Either[MissingFieldException, (Seq[SelectOption], Interaction.ResolvedData)] =
    for {
      values   <- data.selectValues.toEither
      resolved <- data.resolved.toEither
    } yield (values, resolved)

  private def selectMenuExtract[RD, A, CD](
      data: Interaction.MessageComponentData,
      resolvedFields: Interaction.ResolvedData => Either[MissingFieldException, RD],
      makeId: String => Snowflake[A]
  )(
      maps: RD => Seq[Map[Snowflake[A], Any]],
      mapContent: String,
      makeData: (RD, Seq[Snowflake[A]]) => CD
  ): Either[MissingFieldException, CD] =
    (for {
      t <- interactionValuesResolved(data)
      (values, resolved) = t
      rd <- resolvedFields(resolved)

    } yield {
      val ids    = values.map(o => makeId(o.value))
      val mapSeq = maps(rd)

      val missingIds = ids.filter(id => !mapSeq.forall(_.contains(id)))

      if (missingIds.nonEmpty)
        Left(
          MissingFieldException.messageAndData(
            s"Missing $mapContent in resolved object: ${missingIds.mkString(", ")}",
            resolved
          )
        )
      else
        Right(makeData(rd, ids))
    }).flatten

  trait UserSelectMenuComponents[F[_]] extends SpecificComponent[F, SelectMenu, Seq[(User, GuildMember.Partial)]] {
    override def tpe: ComponentType = ComponentType.UserSelect

    def make(
        placeholder: Option[String] = None,
        defaultValues: Seq[UserId] = Nil,
        minValues: Int = 1,
        maxValues: Int = 1,
        disabled: Boolean = false
    )(handler: ComponentInvocation[Seq[(User, GuildMember.Partial)]] => F[HighInteractionResponse[F]]): F[SelectMenu]
  }
  object UserSelectMenuComponents {
    class Default[F[_]](val top: Components[F])(implicit F: MonadError[F, Throwable])
        extends UserSelectMenuComponents[F] {
      override def make(
          placeholder: Option[String],
          defaultValues: Seq[UserId],
          minValues: Int,
          maxValues: Int,
          disabled: Boolean
      )(
          handler: ComponentInvocation[Seq[(User, GuildMember.Partial)]] => F[HighInteractionResponse[F]]
      ): F[SelectMenu] =
        SpecificComponent.makeBase(this, handler) { customId =>
          SelectMenu.make20(
            tpe = ComponentType.UserSelect,
            customId = customId,
            placeholder = UndefOr.fromOption(placeholder),
            defaultValues = UndefOr.fromOption(
              Option.when(defaultValues.nonEmpty)(
                defaultValues.map(id => SelectDefaultValue.make20(id, "user"))
              )
            ),
            minValues = if (minValues != 1) UndefOrSome(minValues) else UndefOrUndefined(),
            maxValues = if (maxValues != 1) UndefOrSome(maxValues) else UndefOrUndefined(),
            disabled = UndefOr.someIfTrue(disabled)
          )
        }

      override def extractData(interaction: Interaction, customId: String): F[Seq[(User, GuildMember.Partial)]] =
        interaction.data match {
          case UndefOrSome(value: Interaction.MessageComponentData) =>
            selectMenuExtract(
              value,
              resolved =>
                (
                  resolved.users.toEither,
                  resolved.members.toEither
                ).tupled,
              s => UserId(s)
            )(
              rd => Seq(rd._1, rd._2),
              "user or member",
              (rd, userIds) => {
                val users   = rd._1
                val members = rd._2
                userIds.map(id => users.get(id).zip(members.get(id))).collect { case Some(v) =>
                  v
                }
              }
            ).fold(F.raiseError, F.pure)

          case _ =>
            F.raiseError(
              MissingFieldException.messageAndData("Interaction data missing or with wrong type", interaction)
            )
        }
    }
  }

  trait RoleSelectMenuComponents[F[_]] extends SpecificComponent[F, SelectMenu, Seq[Role]] {
    override def tpe: ComponentType = ComponentType.RoleSelect

    def make(
        placeholder: Option[String] = None,
        defaultValues: Seq[RoleId] = Nil,
        minValues: Int = 1,
        maxValues: Int = 1,
        disabled: Boolean = false
    )(handler: ComponentInvocation[Seq[Role]] => F[HighInteractionResponse[F]]): F[SelectMenu]
  }
  object RoleSelectMenuComponents {
    class Default[F[_]](val top: Components[F])(implicit F: MonadError[F, Throwable])
        extends RoleSelectMenuComponents[F] {
      override def make(
          placeholder: Option[String],
          defaultValues: Seq[RoleId],
          minValues: Int,
          maxValues: Int,
          disabled: Boolean
      )(
          handler: ComponentInvocation[Seq[Role]] => F[HighInteractionResponse[F]]
      ): F[SelectMenu] =
        SpecificComponent.makeBase(this, handler) { customId =>
          SelectMenu.make20(
            tpe = ComponentType.RoleSelect,
            customId = customId,
            placeholder = UndefOr.fromOption(placeholder),
            defaultValues = UndefOr.fromOption(
              Option.when(defaultValues.nonEmpty)(
                defaultValues.map(id => SelectDefaultValue.make20(id, "role"))
              )
            ),
            minValues = if (minValues != 1) UndefOrSome(minValues) else UndefOrUndefined(),
            maxValues = if (maxValues != 1) UndefOrSome(maxValues) else UndefOrUndefined(),
            disabled = UndefOr.someIfTrue(disabled)
          )
        }

      override def extractData(interaction: Interaction, customId: String): F[Seq[Role]] =
        interaction.data match {
          case UndefOrSome(value: Interaction.MessageComponentData) =>
            selectMenuExtract(
              value,
              resolved => resolved.roles.toEither,
              s => RoleId(s)
            )(
              rd => Seq(rd),
              "role",
              (roles, roleIds) => {
                roleIds.map(id => roles.get(id)).collect { case Some(v) =>
                  v
                }
              }
            ).fold(F.raiseError, F.pure)

          case _ =>
            F.raiseError(
              MissingFieldException.messageAndData("Interaction data missing or with wrong type", interaction)
            )
        }
    }
  }

  trait MentionableSelectMenuComponents[F[_]]
      extends SpecificComponent[F, SelectMenu, Seq[(UserOrRole, Option[GuildMember.Partial])]] {
    override def tpe: ComponentType = ComponentType.MentionableSelect

    def make(
        placeholder: Option[String] = None,
        defaultValuesUsers: Seq[UserId] = Nil,
        defaultValuesRoles: Seq[RoleId] = Nil,
        defaultValuesChannels: Seq[ChannelId] = Nil,
        minValues: Int = 1,
        maxValues: Int = 1,
        disabled: Boolean = false
    )(
        handler: ComponentInvocation[Seq[(UserOrRole, Option[GuildMember.Partial])]] => F[HighInteractionResponse[F]]
    ): F[SelectMenu]
  }
  object MentionableSelectMenuComponents {
    class Default[F[_]](val top: Components[F])(implicit F: MonadError[F, Throwable])
        extends MentionableSelectMenuComponents[F] {

      override def make(
          placeholder: Option[String],
          defaultValuesUsers: Seq[UserId],
          defaultValuesRoles: Seq[RoleId],
          defaultValuesChannels: Seq[ChannelId],
          minValues: Int,
          maxValues: Int,
          disabled: Boolean
      )(
          handler: ComponentInvocation[Seq[(UserOrRole, Option[GuildMember.Partial])]] => F[HighInteractionResponse[F]]
      ): F[SelectMenu] =
        SpecificComponent.makeBase(this, handler) { customId =>
          SelectMenu.make20(
            tpe = ComponentType.MentionableSelect,
            customId = customId,
            placeholder = UndefOr.fromOption(placeholder),
            defaultValues = UndefOr.fromOption(
              Option.when(defaultValuesUsers.nonEmpty || defaultValuesRoles.nonEmpty || defaultValuesChannels.nonEmpty)(
                defaultValuesUsers.map(id => SelectDefaultValue.make20(id, "user")) ++
                  defaultValuesRoles.map(id => SelectDefaultValue.make20(id, "role")) ++
                  defaultValuesChannels.map(id => SelectDefaultValue.make20(id, "channel"))
              )
            ),
            minValues = if (minValues != 1) UndefOrSome(minValues) else UndefOrUndefined(),
            maxValues = if (maxValues != 1) UndefOrSome(maxValues) else UndefOrUndefined(),
            disabled = UndefOr.someIfTrue(disabled)
          )
        }

      override def extractData(
          interaction: Interaction,
          customId: String
      ): F[Seq[(UserOrRole, Option[GuildMember.Partial])]] =
        interaction.data match {
          case UndefOrSome(value: Interaction.MessageComponentData) =>
            val r = for {
              t <- interactionValuesResolved(value)
              (values, resolved) = t
              users   <- resolved.users.toEither
              members <- resolved.members.toEither
              roles   <- resolved.roles.toEither
            } yield {
              val ids = values.map(o => UserOrRoleId(o.value))
              val data: Seq[(UserOrRoleId, Option[UserOrRole], Option[GuildMember.Partial])] = ids.map { id =>
                val m = members.get(UserId(id.toUnsignedLong))
                val u = users.get(UserId(id.toUnsignedLong)).zip(m).map(_._1)
                val r = roles.get(RoleId(id.toUnsignedLong))

                (id, u.orElse(r), m)
              }

              val missingIds = data.collect { case (id, None, _) =>
                id
              }

              if (missingIds.nonEmpty)
                Left(
                  MissingFieldException.messageAndData(
                    s"Missing user or role in resolved object: ${missingIds.mkString(", ")}",
                    resolved
                  )
                )
              else
                Right(data.collect { case (_, Some(ur), m) =>
                  (ur, m)
                })
            }

            r.flatten.fold(F.raiseError, F.pure)

          case _ =>
            F.raiseError(
              MissingFieldException.messageAndData("Interaction data missing or with wrong type", interaction)
            )
        }
    }
  }

  trait ChannelSelectMenuComponents[F[_]] extends SpecificComponent[F, SelectMenu, Seq[Interaction.ResolvedChannel]] {
    override def tpe: ComponentType = ComponentType.ChannelSelect

    def make(
        channelTypes: Seq[Channel.ChannelType] = Nil,
        placeholder: Option[String] = None,
        defaultValues: Seq[ChannelId] = Nil,
        minValues: Int = 1,
        maxValues: Int = 1,
        disabled: Boolean = false
    )(handler: ComponentInvocation[Seq[Interaction.ResolvedChannel]] => F[HighInteractionResponse[F]]): F[SelectMenu]
  }
  object ChannelSelectMenuComponents {
    class Default[F[_]](val top: Components[F])(implicit F: MonadError[F, Throwable])
        extends ChannelSelectMenuComponents[F] {
      override def make(
          channelTypes: Seq[Channel.ChannelType],
          placeholder: Option[String],
          defaultValues: Seq[ChannelId],
          minValues: Int,
          maxValues: Int,
          disabled: Boolean
      )(
          handler: ComponentInvocation[Seq[Interaction.ResolvedChannel]] => F[HighInteractionResponse[F]]
      ): F[SelectMenu] =
        SpecificComponent.makeBase(this, handler) { customId =>
          SelectMenu.make20(
            tpe = ComponentType.UserSelect,
            customId = customId,
            channelTypes = UndefOrSome(channelTypes),
            placeholder = UndefOr.fromOption(placeholder),
            defaultValues = UndefOr.fromOption(
              Option.when(defaultValues.nonEmpty)(
                defaultValues.map(id => SelectDefaultValue.make20(id, "channel"))
              )
            ),
            minValues = if (minValues != 1) UndefOrSome(minValues) else UndefOrUndefined(),
            maxValues = if (maxValues != 1) UndefOrSome(maxValues) else UndefOrUndefined(),
            disabled = UndefOr.someIfTrue(disabled)
          )
        }

      override def extractData(interaction: Interaction, customId: String): F[Seq[Interaction.ResolvedChannel]] =
        interaction.data match {
          case UndefOrSome(value: Interaction.MessageComponentData) =>
            selectMenuExtract(
              value,
              resolved => resolved.channels.toEither,
              s => GuildChannelId(s)
            )(
              rd => Seq(rd),
              "channel",
              (channels, channelIds) => {
                channelIds.map(id => channels.get(id)).collect { case Some(v) =>
                  v
                }
              }
            ).fold(F.raiseError, F.pure)

          case _ =>
            F.raiseError(
              MissingFieldException.messageAndData("Interaction data missing or with wrong type", interaction)
            )
        }
    }
  }
}
