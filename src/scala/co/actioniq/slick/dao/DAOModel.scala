package co.actioniq.slick.dao

import co.actioniq.slick.logging.LoggingSerializer
import play.api.libs.json.{JsNull, JsNumber, JsString, Writes}


/*
  This file contains traits for "models" / case classes that store recordsets from slick
 */

/**
  * Any DAO model that has an id
  * @tparam I IdType
  */
trait IdModel[I <: IDType, L] extends Loggable[L] {
  def id: I
}

trait Loggable[L] {
  def daoModelSerializes: LoggingSerializer[this.type, L]
}

trait LongIdModel[L] extends IdModel[DbLongOptID, L]

/**
  * DAO model with a UUID
  */
trait UUIDModel[L] extends IdModel[DbUUID, L]

