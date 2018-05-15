package co.actioniq.slick.compiled

import java.sql.{PreparedStatement, ResultSet}

import co.actioniq.slick.dao.DbLongOptId
import co.actioniq.slick.impl.AiqSlickProfile

import scala.reflect.ClassTag

abstract class SeqLongOptJdbcType[T <: BoundedSeq[DbLongOptId] : ClassTag] extends AiqSlickProfile.DriverJdbcType[T] {
  override def hasLiteralForm: Boolean = false

  def sqlType: Int = java.sql.Types.INTEGER

  /**
    * This is where the magic happens.  This expands a sequence input into individual items to set parameters.  It
    * will assume 100 max and set the missing parameters to empty / placeholder
    *
    * @param v   sequence of ids
    * @param p   prepared statement
    * @param idx index for parameter
    */
  def setValue(v: T, p: PreparedStatement, idx: Int): Unit = {
    val size = v.seq.length
    for {(x, i) <- v.seq.zipWithIndex} {
      p.setLong(idx + i, x.get)
    }
    for {i <- size until v.limit} {
      p.setLong(i + idx, -1)
    }
  }

  def getValue(r: ResultSet, idx: Int): T =
    BoundedSeq(r.getString(idx).split(",").map(v => DbLongOptId(v.toLong))).asInstanceOf[T]

  def updateValue(
    v: T,
    r: ResultSet,
    idx: Int
  ): Unit = r.updateString(idx, v.seq.map(_.get.toString).mkString(","))

  override def valueToSQLLiteral(value: T): String = {
    if (value eq null) { //scalastyle:ignore
      "NULL"
    } else {
      value.seq.map(v => v.get.toString).mkString(",")
    }
  }
}
class SeqLongOptJdbcTypeBase extends SeqLongOptJdbcType[BoundedSeq[DbLongOptId]]
class SeqLongOptJdbcType100 extends SeqLongOptJdbcType[BoundedSeq100[DbLongOptId]]
class SeqLongOptJdbcType200 extends SeqLongOptJdbcType[BoundedSeq200[DbLongOptId]]
class SeqLongOptJdbcType500 extends SeqLongOptJdbcType[BoundedSeq500[DbLongOptId]]
class SeqLongOptJdbcType1000 extends SeqLongOptJdbcType[BoundedSeq1000[DbLongOptId]]


