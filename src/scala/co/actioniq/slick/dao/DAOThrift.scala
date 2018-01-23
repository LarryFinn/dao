package co.actioniq.slick.dao

import co.actioniq.thrift.{Context, ThriftUtils}
import com.twitter.scrooge.{ThriftStruct, ThriftStructCodec}
import com.twitter.util.Future
import slick.profile.RelationalDriver

import scala.reflect.ClassTag

/**
  * Any class that will handle a generic thrift field (that is the entire object)
  * @tparam V IdModel / case class
  * @tparam U Thrift
  */
trait DAOThrift[V <: IdModel[_], U <: ThriftStruct] {
  val toThrift: V => U
  val toPersisted: (Context, U) => V
}

/**
  * Helper to perform DAO operations with thrift input / output
  * @tparam T slick table
  * @tparam V IdModel / case class
  * @tparam U Thrift
  * @tparam I IDtYpe
  */
protected trait DAOThriftHelper[T <: DAOTable[V, I], V <: IdModel[I], U <: ThriftStruct, I <: IDType]
  extends DAOThrift[V, U] {
  protected val dao: DAO[T, V, I]
  protected val context: Context

  /**
    * Read
    * @return future of sequence of thrift objects
    */
  def read(): Future[Seq[U]] = {
    dao.read().map(_.map(toThrift))
  }

  /**
    * Read by id
    * @param id object id
    * @return future of option of thrift
    */
  def readById(id: I): Future[Option[U]] = {
    dao.readById(id).map(_.map(toThrift))
  }

  /**
    * Read by set of ids
    * @param id set of ids
    * @return future of sequence of thrifts
    */
  def readById(id: Set[I]): Future[Seq[U]] = {
    dao.readById(id).map(_.map(toThrift))
  }

  /**
    * Read by id fail on invalid id
    * @param id object id
    * @return future of thrift or failure if not found
    */
  def readByIdRequired(id: I): Future[U] = {
    dao.readByIdRequired(id).map(toThrift)
  }

  /**
    * Read by ids fail on invalid id
    * @param id set of object ids
    * @return future of sequence of thrifts
    */
  def readByIdRequired(id: Set[I]): Future[Seq[U]] = {
    dao.readByIdRequired(id).map(_.map(toThrift))
  }

  /**
    * Create a thrift
    * @param input thrift object
    * @return future of id or failure
    */
  def create(input: U): Future[I] = {
    dao.create(toPersisted(context, input))
  }

  /**
    * Create and read a thrift
    * @param input thrift object
    * @return future of thrift object or failure
    */
  def createAndRead(input: U): Future[U] = {
    dao.createAndRead(toPersisted(context, input)).map(toThrift)
  }

  /**
    * Create a sequence of thrift objects
    * @param input seq of thrift objects
    * @return future of sequence of ids or failure
    */
  def create(input: Seq[U]): Future[Seq[I]] = {
    dao.create(input.map(item => toPersisted(context, item)))
  }

  /**
    * Create and read sequence of thrift objects
    * @param input seq of thrift objects
    * @return future of sequence of thrift objects or failure
    */
  def createAndRead(input: Seq[U]): Future[Seq[U]] = {
    dao.createAndRead(input.map(item => toPersisted(context, item))).map(_.map(toThrift))
  }

  /**
    * Update thrift object
    * @param input thrift object
    * @return future of id or failure
    */
  def update(input: U): Future[I] = {
    dao.update(toPersisted(context, input))
  }

  /**
    * Update and read a thrift object
    * @param input thrift object
    * @return future of thrift object or failure
    */
  def updateAndRead(input: U): Future[U] = {
    dao.updateAndRead(toPersisted(context, input)).map(toThrift)
  }

  /**
    * Update a seq of thrift objects
    * @param input seq of thrift objects
    * @return future of seq of ids or failure
    */
  def update(input: Seq[U]): Future[Seq[I]] = {
    dao.update(input.map(item => toPersisted(context, item)))
  }

  /**
    * Update and read a seq of thrift objects
    * @param input seq of thrift objects
    * @return future of seq of thrift objects or failure
    */
  def updateAndRead(input: Seq[U]): Future[Seq[U]] = {
    dao.updateAndRead(input.map(item => toPersisted(context, item))).map(_.map(toThrift))
  }

  /**
    * Delete a thrift object... maybe redundant from DAO
    * @param inputId object id
    * @return future of rows updated
    */
  def delete(inputId: I): Future[Int] = {
    dao.delete(inputId)
  }
}

/**
  * Helper class to persist any thrift type to a string db field.  You need implicit evidence of the columntype to
  * interact with the column
  * Example: implicit val fieldType = new ThriftSlick(driver, SegmentNode).columnType
  * @param driver db driver
  * @param thriftType class type
  * @tparam U thrift type
  */
class ThriftSlick[U <: ThriftStruct: ClassTag](val driver: RelationalDriver, thriftType: ThriftStructCodec[U]) {
  import driver.api._ // scalastyle:ignore
  type DriverType = BaseColumnType[U]

  /**
    * The actual column mapping
    * @return evidence of mapping
    */
  def columnType: DriverType  = MappedColumnType.base[U, String](
    {thrift => ThriftUtils.thriftToString(thrift)},
    {strValue => ThriftUtils.stringToThrift(thriftType, strValue)}
  )
}

/**
  * Ease of use functions for thrift type to db field
  */
object ThriftSlick {
  /**
    * Generic apply function
    * @param driver db driver
    * @param thriftType class type
    * @tparam U thrift type
    * @return
    */
  def apply[U <: ThriftStruct: ClassTag](driver: RelationalDriver, thriftType: ThriftStructCodec[U]):
  ThriftSlick[U] = {
    new ThriftSlick[U](driver, thriftType)
  }

  /**
    * Skip over object instantiation and just get column mapping.
    * Example: implicit val myType = ThriftSlick.columnType(driver, SegmentNode)
    * @param driver db driver
    * @param thriftType class type
    * @tparam U thrift type
    * @return evidence of mapping
    */
  def columnType[U <: ThriftStruct: ClassTag](driver: RelationalDriver, thriftType: ThriftStructCodec[U]):
  ThriftSlick[U]#DriverType = {
    ThriftSlick(driver, thriftType).columnType
  }
}

