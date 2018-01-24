package co.actioniq.slick.logging

import java.util.UUID

import grizzled.slf4j.Logger

import scala.collection.mutable.ListBuffer

trait Backend[IN <: BasicLoggingModel, OUT] {
  def write(data: IN): Unit
  def flush()(implicit serializer: LoggingSerializer[IN, OUT]): Unit
  def clear(): Unit
}

trait LoggerFileBackend[IN <: BasicLoggingModel, OUT] extends Backend[IN, OUT] {
  private final lazy val transLogger: Logger = Logger("co.actioniq.slick.logging.LoggerFileBackend")
  val changes: ListBuffer[IN] = ListBuffer()
  override def write(data: IN): Unit = {
    this.synchronized {
      changes += data
    }
  }

  override def clear(): Unit = {
    this.synchronized{
      changes.clear()
    }
  }

  override def flush()(implicit serializer: LoggingSerializer[IN, OUT]): Unit = {
    val trxId = UUID.randomUUID().toString
    val localList = this.synchronized {
      val temp = changes.toList
      changes.clear()
      temp
    }
    localList.foreach(change => {
      val changeWithTrxId = serializer.serialize(change, trxId)
      transLogger.info(changeWithTrxId.toString)
    })
  }
}

trait NoopBackend[IN <: BasicLoggingModel, OUT] extends Backend[IN, OUT] {
  def write(data: IN): Unit
  def flush()(implicit serializer: LoggingSerializer[IN, OUT]): Unit
  def clear(): Unit
}

trait LoggingSerializer[-IN, OUT] {
  def serialize(in: IN, transactionId: String): OUT
}
