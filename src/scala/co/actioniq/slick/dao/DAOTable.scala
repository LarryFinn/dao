package co.actioniq.slick.dao

import slick.lifted.Tag
import java.sql.{PreparedStatement, ResultSet}

import slick.jdbc.{JdbcProfile, MySQLProfile}

/**
  * Basic table struction
  * @tparam V model
  * @tparam I IDType
  */
trait DAOTable[V <: IdModel[I], I <: IDType] extends IdTable[I]{
  final type TableElementType = V
}

/**
  * A table that has an IDType for an ID
  * @tparam I IDType
  */
trait IdTable[I <: IDType]{
  def id: slick.lifted.Rep[I]
}


/**
  * Lowest common denominator of tables, just has an ID.
  * @param tag table tag
  * @param tableName db table name
  * @param schemaName optional schema name
  * @tparam V type of case class to store records
  * @tparam I IDType id type of table
  */
abstract class MySQLDAOTable[V <: IdModel[I], I <: IDType](tag: Tag, tableName: String, schemaName: Option[String] = None)
  extends MySQLProfile.Table[V](tag, schemaName, tableName)
    with DAOTable[V, I] with MySQLImplicits


/**
  * Table that has a Option[Long] id
  * @param tag table tag
  * @param tableName db table name
  * @param schemaName optional schema name
  * @tparam V type of case class to store records
  */
abstract class MySQLLongIdTable[V <: IdModel[DbLongOptID]](tag: Tag, tableName: String, schemaName: Option[String] = None)
  extends MySQLDAOTable[V, DbLongOptID](tag, tableName, schemaName)
    with MySQLImplicits {

  def id: slick.lifted.Rep[DbLongOptID] = column[DbLongOptID] ("id", O.AutoInc)
}

/**
  * Table that has a UUID (stored as binary) id
  * @param tag table tag
  * @param tableName db table name
  * @param schemaName optional schema name
  * @tparam V type of case class to store records
  */
abstract class MySQLUUIDTable[V <: IdModel[DbUUID]](tag: Tag, tableName: String, schemaName: Option[String] = None)
  extends MySQLDAOTable[V, DbUUID](tag, tableName, schemaName)
    with MySQLImplicits {

  def id: slick.lifted.Rep[DbUUID] = column[DbUUID] ("id")
}

trait MySQLImplicits extends JdbcAiqTypes {
  override protected val profile = MySQLProfile

  protected implicit val dbBinArrayJdbcType = (new profile.JdbcTypes).byteArrayJdbcType
  protected implicit val dbUuidJdbcType = uuidJdbcType
  protected implicit val dbUUIDColumnType = profile.api.MappedColumnType.base[DbUUID, Array[Byte]](
    {uuid => uuid.binValue},
    {bin => DbUUID(bin)}
  )
  protected implicit val dbOptLongJdbcType = optLongJdbcType

  protected implicit val timestampCol = {
    (new profile.JdbcTypes).timestampJdbcType
  }
}
trait JdbcAiqTypes{
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

  def optLongJdbcType(): profile.DriverJdbcType[DbLongOptID] = {
    new profile.DriverJdbcType[DbLongOptID]() {
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
  }
}

trait AiqDelete extends JdbcProfile { driver: JdbcProfile =>
  import scala.language.higherKinds // scalastyle:ignore
  trait API extends super.API {
    implicit def queryDeleteActionExtensionMethodsToo[C[_]](q: Query[_ <: DAOTable[_, _], _, C]):
    DeleteActionExtensionMethods =
      createDeleteActionExtensionMethods(deleteCompiler.run(q.toNode).tree, ())
  }

  override val api: API = new API {}
}

trait AiqProfile extends JdbcProfile with AiqDelete{ driver =>
  override val profile: AiqDelete = this.asInstanceOf[AiqDelete]
}


