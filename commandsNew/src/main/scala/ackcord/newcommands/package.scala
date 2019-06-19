package ackcord

import akka.NotUsed

package object newcommands {

  type GuildUserCommandMessage[+A]        = GuildCommandMessage[A] with UserCommandMessage[A]
  type VoiceGuildMemberCommandMessage[+A] = GuildMemberCommandMessage[A] with VoiceGuildCommandMessage[A]

  type Command[A]      = ackcord.newcommands.ComplexCommand[A, NotUsed]
  type NamedCommand[A] = ackcord.newcommands.NamedComplexCommand[A, NotUsed]
}
