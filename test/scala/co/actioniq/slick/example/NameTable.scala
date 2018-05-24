package co.actioniq.slick.example

trait NameTable {
  def name: slick.lifted.Rep[String]
}
