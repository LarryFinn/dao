package co.actioniq.slick.dao

import java.sql.{PreparedStatement, ResultSet}

import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile, PostgresProfile}
import slick.lifted.{AbstractTable, Tag}

/**
  * A table that has an IDType for an ID
 *
  * @tparam I IDType
  */
trait IdTable[I <: IdType]{
  def id: slick.lifted.Rep[I]
}

object DAOTable {
  type Table[V <: DAOModel[I], I <: IdType, P <: JdbcProfile] = P#Table[V] with IdTable[I]
}

abstract class MySQLDAOTable[V <: DAOModel[I], I <: IdType](
  tag: Tag,
  tableName: String,
  schemaName: Option[String] = None
) extends MySQLProfile.Table[V](tag, schemaName, tableName)
    with IdTable[I]{
  self: DAOTable.Table[V, I, MySQLProfile] =>
}

abstract class PostgresDAOTable[V <: DAOModel[I], I <: IdType](
  tag: Tag,
  tableName: String,
  schemaName: Option[String] = None
) extends PostgresProfile.Table[V](tag, schemaName, tableName)
    with IdTable[I]{
  self: DAOTable.Table[V, I, PostgresProfile] =>
}

abstract class H2DAOTable[V <: DAOModel[I], I <: IdType](
  tag: Tag,
  tableName: String,
  schemaName: Option[String] = None
) extends H2Profile.Table[V](tag, schemaName, tableName)
  with IdTable[I]{
  self: DAOTable.Table[V, I, H2Profile] =>
}

trait JdbcTypes{
  protected val profile: JdbcProfile
  def uuidJdbcType(): profile.DriverJdbcType[DbUUID] = {
    new profile.DriverJdbcType[DbUUID]() {
      def sqlType: Int = java.sql.Types.BINARY
      def setValue(v: DbUUID, p: PreparedStatement, idx: Int): Unit = p.setBytes(idx, v.binValue)
      def getValue(r: ResultSet, idx: Int): DbUUID = DbUUID(r.getBytes(idx))
      def updateValue(v: DbUUID, r: ResultSet, idx: Int): Unit = r.updateBytes(idx, v.binValue)
      override def hasLiteralForm: Boolean = false
    }
  }

  def optLongJdbcType(): profile.DriverJdbcType[DbLongOptId] = {
    new profile.DriverJdbcType[DbLongOptId]() {
      def sqlType: Int = java.sql.Types.BIGINT
      def setValue(v: DbLongOptId, p: PreparedStatement, idx: Int): Unit = v.value match {
        case None => p.setNull(idx, sqlType)
        case Some(s) => p.setLong(idx, s)
      }
      def getValue(r: ResultSet, idx: Int): DbLongOptId = DbLongOptId(r.getLong(idx))
      def updateValue(v: DbLongOptId, r: ResultSet, idx: Int): Unit = v.value match {
        case None => r.updateNull(idx)
        case Some(s) => r.updateLong(idx, s)
      }
      override def hasLiteralForm: Boolean = false
    }
  }
}


