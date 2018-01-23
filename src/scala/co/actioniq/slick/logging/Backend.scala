package co.actioniq.slick.logging

import java.util.UUID

import co.actioniq.slick.dao.IdModel
import grizzled.slf4j.Logger
import play.api.libs.json.{JsObject, JsString, Json, Writes}

import scala.collection.mutable.ListBuffer

trait Backend {
  def write[T <: IdModel[_]](data: LoggingModel)(implicit writes: Writes[T]): Unit
  def flush(): Unit
  def clear(): Unit
}

trait LoggerFileBackend extends Backend {
  private final lazy val transLogger: Logger = Logger("co.actioniq.slick.logging.LoggerFileBackend") //scalastyle:ignore
  val changes: ListBuffer[LoggingModel] = ListBuffer()
  override def write[T <: IdModel[_]](data: LoggingModel)(implicit writes: Writes[T]): Unit = {
    this.synchronized {
      changes += data
    }
  }

  override def clear(): Unit = {
    this.synchronized{
      changes.clear()
    }
  }

  override def flush(): Unit = {
    val trxId = UUID.randomUUID().toString
    val localList = this.synchronized {
      val temp = changes.toList
      changes.clear()
      temp
    }
    localList.foreach(change => {
      val changeWithTrxId = Json.toJson(change).asInstanceOf[JsObject] + ("transaction_id" -> JsString(trxId))
      transLogger.info(changeWithTrxId.toString())
    })
  }
}

trait NoopBackend extends Backend {
  override def write[T <: IdModel[_]](data: LoggingModel)(implicit writes: Writes[T]): Unit = Unit

  override def flush(): Unit = Unit

  override def clear(): Unit = Unit
}
