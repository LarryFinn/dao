package co.actioniq.slick.logging

import java.util.Date

import co.actioniq.slick.logging.TransactionAction.TransactionAction

trait BasicLoggingModel {
  protected val createdAt: Date
  protected val action: TransactionAction
}

object TransactionAction extends Enumeration {
  type TransactionAction = Value
  val create = Value("create")
  val update = Value("update")
  val delete = Value("delete")
}

