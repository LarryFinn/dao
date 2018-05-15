package co.actioniq.slick.compiled

/**
  * Placeholder class for compiled queries.  More or less an empty ?
  */
class Placeholder() extends Ordered[Placeholder]{
  override def compare(that: Placeholder): Int = 0
}
