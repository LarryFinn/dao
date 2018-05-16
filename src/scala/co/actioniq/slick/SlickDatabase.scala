package co.actioniq.slick

import co.actioniq.slick.logging.TransactionLogger
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext

class SlickDatabase(val self: Database, val transactionLogger: TransactionLogger)
  extends Proxy {
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

object SlickDatabase {
  def apply(
    db: Database,
    transactionLogger: TransactionLogger
  ): SlickDatabase = new SlickDatabase(db, transactionLogger)
  implicit def database2SlickDatabase(db: Database)(implicit transactionLogger: TransactionLogger): SlickDatabase =
    SlickDatabase(db, transactionLogger)
  implicit def slickDatabase2Database(slickDb: SlickDatabase): Database = slickDb.self
}
