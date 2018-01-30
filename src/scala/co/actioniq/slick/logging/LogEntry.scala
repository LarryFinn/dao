package co.actioniq.slick.logging

import TransactionAction.TransactionAction


trait LogEntry {
  protected val action: TransactionAction
}
