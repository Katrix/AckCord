package ackcord

package object interactions {
  type ~[A, B] = (A, B)
  object ~ {
    def unapply[A, B](t: (A, B)): Some[(A, B)] = Some(t)
  }

  type Interaction = data.Interaction
  val Interaction: data.Interaction.type = data.Interaction
}
