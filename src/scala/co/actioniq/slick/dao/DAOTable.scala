package co.actioniq.slick.dao

import co.actioniq.slick.AiqSlickTable
import slick.lifted.Tag
import slick.profile.RelationalDriver
import java.sql.{PreparedStatement, ResultSet}
import slick.driver.MySQLDriver.DriverJdbcType
import slick.driver.MySQLDriver.JdbcTypes

/**
  * A table that has an IDType for an ID
  * @tparam I IDType
  */
trait IdTable[I <: IDType]{
  def id: slick.lifted.Rep[I]
  protected implicit def timestampColumnType = (new JdbcTypes).timestampJdbcType
}

/**
  * A Table that has a customer_id field
  */
trait CustomerTable {
  def customerId: slick.lifted.Rep[Long]
}

/**
  * A table that has a team_id field
  */
trait TeamTable {
  def teamId: slick.lifted.Rep[Option[String]]
}

/**
  * Lowest common denominator of tables, just has an ID.  Has to be an abstract class because functions are needed from
  * AiqSlickTable
  * @param driver slick db driver
  * @param tag table tag
  * @param tableName db table name
  * @tparam V type of case class to store records
  * @tparam I IDType id type of table
  */
abstract class DAOTable[V, I <: IDType](driver: RelationalDriver, tag: Tag, tableName: String)
  extends AiqSlickTable[V](driver, tag, tableName)
    with IdTable[I]

/**
  * Table that has a Option[Long] id
  * @param driver slick db driver
  * @param tag table tag
  * @param tableName db table name
  * @tparam V type of case class to store records
  */
abstract class LongIdTable[V](driver: RelationalDriver, tag: Tag, tableName: String)
  extends DAOTable[V, DbLongOptID](driver, tag, tableName)
    with IdTable[DbLongOptID]
    with DbImplicits {

  override protected implicit val dbOptLongJdbcType = new OptLongJdbcType

  def id: slick.lifted.Rep[DbLongOptID] = column[DbLongOptID] ("id", O.AutoInc)
}

trait DbImplicits {
  import slick.driver.MySQLDriver.JdbcTypes // scalastyle:ignore
  protected implicit val dbBinArrayJdbcType = (new JdbcTypes).byteArrayJdbcType
  protected implicit val dbUuidJdbcType = new UUIDJdbcType
  protected implicit val dbUUIDColumnType = slick.driver.MySQLDriver.api.MappedColumnType.base[DbUUID, Array[Byte]](
    {uuid => uuid.binValue},
    {bin => DbUUID(bin)}
  )
  protected implicit val dbOptLongJdbcType = new OptLongJdbcType
}
/**
  * Table that has a UUID (stored as binary) id
  * @param driver slick db driver
  * @param tag table tag
  * @param tableName db table name
  * @tparam V type of case class to store records
  */
abstract class UUIDTable[V](driver: RelationalDriver, tag: Tag, tableName: String)
  extends DAOTable[V, DbUUID](driver, tag, tableName)
    with IdTable[DbUUID]
    with DbImplicits {

  def id: slick.lifted.Rep[DbUUID] = column[DbUUID] ("id")
}

/**
  * Table that has an Option[Long] id and customer_id
  * @param driver slick db driver
  * @param tag table tag
  * @param tableName db table name
  * @tparam V type of case class to store records
  */
abstract class LongIdCustomerTable[V](driver: RelationalDriver, tag: Tag, tableName: String)
  extends LongIdTable[V](driver, tag, tableName)
    with IdTable[DbLongOptID] with CustomerTable{
  import driver.api._ // scalastyle:ignore
  def customerId: slick.lifted.Rep[Long] = column[Long] ("customer_id")
}

/**
  * Table that has a UUID id and customer_id
  * @param driver slick db driver
  * @param tag table tag
  * @param tableName db table name
  * @tparam V type of case class to store records
  */
abstract class UUIDCustomerTable[V](driver: RelationalDriver, tag: Tag, tableName: String)
  extends UUIDTable[V](driver, tag, tableName)
    with IdTable[DbUUID] with CustomerTable{
  import driver.api._ // scalastyle:ignore
  def customerId: slick.lifted.Rep[Long] = column[Long] ("customer_id")
}

/**
  * Table that has an Option[Long] id, customer_id, and generic thrift column
  * @param driver slick db driver
  * @param tag table tag
  * @param tableName db table name
  * @tparam V type of case class to store records
  */
abstract class LongIdCustomerThriftTable[V](driver: RelationalDriver, tag: Tag, tableName: String)
extends LongIdCustomerTable[V](driver, tag, tableName) with CustomerTable{
  import driver.api._ // scalastyle:ignore
  def thrift: slick.lifted.Rep[String] = column[String] ("thrift")
}

/**
  * Table that has a UUID id, customer_id and generic thrift column
  * @param driver slick db driver
  * @param tag table tag
  * @param tableName db table name
  * @tparam V type of case class to store records
  */
abstract class UUIDCustomerThriftTable[V](driver: RelationalDriver, tag: Tag, tableName: String)
  extends UUIDCustomerTable[V](driver, tag, tableName) with CustomerTable{
  import driver.api._ // scalastyle:ignore
  def thrift: slick.lifted.Rep[String] = column[String] ("thrift")
}


/**
  * Helper class for parsing UUID from jdbc recordset
  */
class UUIDJdbcType extends DriverJdbcType[DbUUID] {
  def sqlType: Int = java.sql.Types.BINARY
  def setValue(v: DbUUID, p: PreparedStatement, idx: Int): Unit = p.setBytes(idx, v.binValue)
  def getValue(r: ResultSet, idx: Int): DbUUID = DbUUID(r.getBytes(idx))
  def updateValue(v: DbUUID, r: ResultSet, idx: Int): Unit = r.updateBytes(idx, v.binValue)
  override def hasLiteralForm: Boolean = false
}

/**
  * Helper class for parsing Option[Long] from jdbc recordset
  */
class OptLongJdbcType extends DriverJdbcType[DbLongOptID] {
  def sqlType: Int = java.sql.Types.BIGINT
  def setValue(v: DbLongOptID, p: PreparedStatement, idx: Int): Unit = v.value match {
    case None => p.setNull(idx, sqlType)
    case Some(s) => p.setLong(idx, s)
  }
  def getValue(r: ResultSet, idx: Int): DbLongOptID = DbLongOptID(r.getLong(idx))
  def updateValue(v: DbLongOptID, r: ResultSet, idx: Int): Unit = v.value match {
    case None => r.updateNull(idx)
    case Some(s) => r.updateLong(idx, s)
  }
  override def hasLiteralForm: Boolean = false
}
