package co.actioniq.slick.logging

object TransactionAction extends Enumeration {
  type TransactionAction = Value
  val create = Value("create")
  val update = Value("update")
  val delete = Value("delete")
}
