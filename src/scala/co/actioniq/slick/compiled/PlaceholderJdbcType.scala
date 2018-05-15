package co.actioniq.slick.compiled

import java.sql.{PreparedStatement, ResultSet}

import co.actioniq.slick.impl.AiqSlickProfile

/**
  * Describe to slick how to interact with a placeholder
  */
class PlaceholderJdbcType extends AiqSlickProfile.DriverJdbcType[Placeholder] {
  override def hasLiteralForm: Boolean = true

  def sqlType: Int = java.sql.Types.OTHER

  def setValue(v: Placeholder, p: PreparedStatement, idx: Int): Unit = {}

  def getValue(r: ResultSet, idx: Int): Placeholder = new Placeholder()

  def updateValue(v: Placeholder, r: ResultSet, idx: Int): Unit = {}

  override def valueToSQLLiteral(value: Placeholder): String = {
    if (value eq null) { //scalastyle:ignore
      "NULL"
    } else {
      ""
    }
  }
}
