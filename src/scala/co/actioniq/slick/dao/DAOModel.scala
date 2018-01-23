package co.actioniq.slick.dao

import co.actioniq.thrift.ThriftUtils
import com.twitter.scrooge.ThriftStruct
import play.api.libs.json.{JsNull, JsNumber, JsString, Json, Writes}


/*
  This file contains traits for "models" / case classes that store recordsets from slick
 */

/**
  * Any DAO model that has an id
  * @tparam I IdType
  */
trait IdModel[I <: IDType] extends Jsonable {
  def id: I
}

trait Jsonable {
  def daoModelWrites: Writes[_ <: Jsonable]
}

/**
  * Any DAO model that has a customer_id field
  */
trait CustomerModel{
  def customerId: Long
}

/**
  * Any DAO model that has a team_id field
  */
trait TeamModel{
  def teamId: Option[String]
}

/**
  * Any DAO model that has a customer_id and id field
  * @tparam I IdType
  */
trait IdCustomerModel[I<: IDType] extends IdModel[I] with CustomerModel

/**
  * Any DAO model that has an id, customer_id, and generic thrift field
  * @tparam I IdType
  */
trait IdCustomerThriftModel[I <: IDType] extends IdCustomerModel[I]{
  def thrift: String
}

//Helper traits for common model combinations

/**
  * DAO model with an Option[Long] id
  */
trait LongIdModel extends IdModel[DbLongOptID] with DbLongOptIdWriter

/**
  * DAO model with a UUID
  */
trait UUIDModel extends IdModel[DbUUID] with DbUUIDWriter

/**
  * DAO model with an Option[Long] id and customer_id
  */
trait LongIdCustomerModel extends IdCustomerModel[DbLongOptID] with DbLongOptIdWriter

/**
  * DAO model with a UUID and customer_id
  */
trait UUIDCustomerModel extends IdCustomerModel[DbUUID] with DbUUIDWriter

/**
  * DAO model with an Option[Long] id, customer_id, and generic thrift
  */
trait LongIdCustomerThriftModel extends IdCustomerThriftModel[DbLongOptID] with DbLongOptIdWriter

/**
  * Json writer for DbUUID
  */
trait DbUUIDWriter {
  implicit val dbUUIDWriter = new Writes[DbUUID] {
    override def writes(o: DbUUID) = JsString(o.toString)
  }
}

trait DbLongOptIdWriter {
  implicit val dbLongOptIdWriter = new Writes[DbLongOptID] {
    override def writes(o: DbLongOptID) = o.value.map(JsNumber(_)).getOrElse(JsNull)
  }
}

trait ThriftWriter[T <: ThriftStruct] {
  implicit val thriftWriter = new Writes[T] {
    override def writes(o: T) = ThriftUtils.thriftToSimpleJson(o)
  }
}
