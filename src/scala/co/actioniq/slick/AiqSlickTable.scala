package co.actioniq.slick

import java.net.URI

import slick.ast.{ColumnOption, FieldSymbol, Select, SimpleTableIdentitySymbol, TableIdentitySymbol, TypedType}
import slick.driver.{JdbcDriver, JdbcProfile}
import slick.lifted.{AbstractTable, RefTag, Rep, Tag}
import slick.profile.{RelationalDriver, RelationalProfile}

/**
  * AIQ-specific type-converters to/from their database representations.
  */
class AiqSlickConverters(val driver: RelationalDriver) {
  import driver.api._ // scalastyle:ignore
  implicit val uriColumnType = MappedColumnType.base[URI, String]( // scalastyle:ignore
    { uri => uri.toASCIIString },
    { uriString => URI.create(uriString) }
  )
}

trait AiqDelete extends JdbcProfile { driver: JdbcDriver =>
  import scala.language.higherKinds // scalastyle:ignore
  trait API extends super.API {
    implicit def queryDeleteActionExtensionMethodsToo[C[_]](q: Query[_ <: AiqSlickTable[_], _, C]):
    DeleteActionExtensionMethods =
      createDeleteActionExtensionMethods(deleteCompiler.run(q.toNode).tree, ())
  }

  override val api: API = new API {}
}

trait AiqDriver extends JdbcDriver with AiqDelete{ driver =>
  override val profile: AiqDelete = this.asInstanceOf[AiqDelete]
}

object AiqH2Driver extends slick.driver.H2Driver with AiqDriver
object AiqMySQLDriver extends slick.driver.MySQLDriver with AiqDriver

/**
  * Various options for tables.
  */
object AiqSlickColumnOptions {

  val AutoInc = ColumnOption.AutoInc
  val Length = RelationalProfile.ColumnOption.Length
  val PrimaryKey = ColumnOption.PrimaryKey

  def default[T](defaultValue: T): RelationalProfile.ColumnOption.Default[T] = {
    RelationalProfile.ColumnOption.Default[T](defaultValue)
  }
}

/**
  * The core of this class was copied from slick's [[AbstractTable]].
  * This was done so that tables could be represented by top-level scala
  * classes instead of classes embedded within traits. The standard
  * slick way would have required that all our DAOs turn into traits,
  * where tables are injected via self-types. By making the base table
  * class a top level one, we don't have to do this anymore.
  */
abstract class AiqSlickTable[T](
  private val driver: RelationalDriver,
  override val tableTag: Tag,
  override val tableName: String,
  override val schemaName: Option[String] = None) extends AbstractTable[T](tableTag, schemaName, tableName) {

  override type TableElementType = T

  val O = AiqSlickColumnOptions

  def tableIdentitySymbol: TableIdentitySymbol = SimpleTableIdentitySymbol(driver, schemaName.getOrElse("_"), tableName)

  /**
    * Note that Slick uses VARCHAR or VARCHAR(254) in DDL for String
    * columns if neither ColumnOption DBType nor Length are given.
    */
  def column[C](n: String, options: ColumnOption[C]*)(implicit tt: TypedType[C]): Rep[C] = {
    if(tt == null) throw new NullPointerException( // scalastyle:ignore
        "implicit TypedType[C] for column[C] is null. " +
        "This may be an initialization order problem. " +
        "When using a MappedColumnType, you may want to change it from a val to a lazy val or def."
      )
    new Rep.TypedRep[C] {
      override def toNode = {
        val in = tableTag match {
          case r: RefTag => r.path
          case _ => tableNode
        }
        val fieldSymbol = FieldSymbol(n)(options, tt)
        Select(in, fieldSymbol) :@ tt
      }
      override def toString = (tableTag match {
        case r: RefTag => "(" + tableName + " " + r.path + ")"
        case _ => tableName
      }) + "." + n
    }
  }
}
