package co.actioniq.slick.example

import co.actioniq.slick.dao.DbUUID
import co.actioniq.slick.logging.LogEntry
import co.actioniq.slick.logging.TransactionAction.TransactionAction

case class LoggingModel(override val action: TransactionAction, id: DbUUID, name: String) extends LogEntry
