package co.actioniq.slick.dao

import slick.jdbc.JdbcProfile

/**
  * Default filter is the "lowest" level of the dao traits that simply lets you add a default filter to any query that
  * the DAO runs.  This is useful for customer_id, team_id, etc...
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  * @tparam I id type (option long and uuid)
  */
trait DefaultFilter[T <: DAOTable.Table[V, I, P], V <: IdModel[I], I <: IdType, P <: JdbcProfile] {
  protected val profile: JdbcProfile
  import profile.api._ // scalastyle:ignore

  protected type QueryWithFilter =
    Query[T, T#TableElementType, Seq]
  protected val slickQuery: TableQuery[T]
  private var defaultFilters: List[T => Rep[Boolean]] = List()
  private var defaultOptFilters: List[T => Rep[Option[Boolean]]] = List()

  def getSlickQuery: TableQuery[T] = slickQuery

  protected def addDefaultFilter(filter: T => Rep[Boolean]): Unit = {
    defaultFilters = defaultFilters.::(filter)
  }

  protected def addDefaultOptFilter(filter: T => Rep[Option[Boolean]]): Unit = {
    defaultOptFilters = defaultOptFilters.::(filter)
  }

  private def reduceFilters(t: T) = defaultFilters.reduceLeft((original, next) => inT => original(inT) && next(inT))(t)

  private def reduceOptFilters(t: T) = defaultOptFilters
    .reduceLeft((original, next) => inT => original(inT) && next(inT))(t)

  def getDefaultFilters(t: T): Rep[Option[Boolean]] = {
    (defaultFilters, defaultOptFilters) match {
      case (Nil, Nil) => Option(true).bind
      case (Nil, _) => reduceOptFilters(t)
      case (_, Nil) => reduceFilters(t)
      case (_, _) => reduceFilters(t) && reduceOptFilters(t)
    }
  }
}






