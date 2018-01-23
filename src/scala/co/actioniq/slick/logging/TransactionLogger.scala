package co.actioniq.slick.logging

trait TransactionLogger extends Backend

object TransactionLogger {
  trait Provider {
    protected def getTransactionLogger: TransactionLogger
    lazy implicit val implicitLogger = getTransactionLogger
  }

  trait ProviderProd extends Provider {
    override def getTransactionLogger: TransactionLogger = new TransactionLogger with LoggerFileBackend
  }

  trait ProviderNoop extends Provider {
    override def getTransactionLogger: TransactionLogger = new TransactionLogger with NoopBackend
  }
}
