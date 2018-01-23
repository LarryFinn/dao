package co.actioniq.slick

import co.actioniq.functional.AiqLogger
import co.actioniq.slick.logging.TransactionLogger
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class AiqDatabase(val self: Database, val transactionLogger: TransactionLogger)
  extends Proxy with AiqLogger {
  def run[R](a: slick.dbio.DBIOAction[R, slick.dbio.NoStream, scala.Nothing])(implicit ec: ExecutionContext):
  scala.concurrent.Future[R] = {
    self.run(a).transform(result => {
      transactionLogger.flush()
      result
    }, failed => {
      transactionLogger.clear()
      failed
    })
  }
}

object AiqDatabase {
  def apply(db: Database, transactionLogger: TransactionLogger): AiqDatabase = new AiqDatabase(db, transactionLogger)
  implicit def database2AiqDatabase(db: Database)(implicit transactionLogger: TransactionLogger): AiqDatabase =
    AiqDatabase(db, transactionLogger)
  implicit def aiqDatabase2Database(aiqDb: AiqDatabase): Database = aiqDb.self
}
