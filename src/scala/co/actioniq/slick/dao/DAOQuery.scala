package co.actioniq.slick.dao


import co.actioniq.slick.AiqDriver
import co.actioniq.thrift.Context

import scala.concurrent.ExecutionContext

/**
  * Generates the base queries that most DAO classes will need
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  * @tparam I id type (option long and uuid)
  */
trait DAOQuery[T <: DAOTable[V, I], V <: IdModel[I], I <: IDType]
  extends DefaultFilter[T, V, I] with IdQuery[T, V, I]{
  protected val driver: AiqDriver
  protected val context: Context
  import driver.api._ // scalastyle:ignore

  //Name of object, used for generic error messages like in update "id does not exist for $nameSingle"
  def nameSingle: String
  //type alias for query
  type QueryJoin[A, B] = Query[(T, A), (V, B), Seq]
  type QueryJoinTwo[A, B, AA, BB] = Query[((T, A), AA), ((V, B), BB), Seq]
  //type alias for left join query
  type QueryLeftJoin[A, B] = Query[(T, Rep[Option[A]]), (V, Option[B]), Seq]

  /**
    * Build an inner join query between two daos
    * @param other other DAO to join to
    * @param on lambda filter function to specify "on" clause for join
    * @param extraQueryOps extra where clause
    * @tparam A type of other slick table
    * @tparam B type of other slick model
    * @tparam C type of other idtype
    * @return query to do join
    */
  def joinQuery[A <: DAOTable[B, C], B <: IdModel[C], C <: IDType]
  (
    other: DAOQuery[A, B, C],
    on: (T, A) => Rep[Option[Boolean]],
    extraQueryOps: QueryJoin[A, B] => QueryJoin[A, B] = (query: QueryJoin[A, B]) => query
  ):
  QueryJoin[A, B]= {
    extraQueryOps(
      slickQuery.join(other.getSlickQuery).on((mine, theirs) => on(mine, theirs))
        .filter(f => getDefaultFilters(f._1) && other.getDefaultFilters(f._2))
    )
  }

  def joinQueryTwo[A <: DAOTable[B, C], B <: IdModel[C], C <: IDType,
  AA <: DAOTable[BB, CC], BB <: IdModel[CC], CC <: IDType]
  (
    otherFirst: DAOQuery[A, B, C],
    onFirst: (T, A) => Rep[Option[Boolean]],
    otherSecond: DAOQuery[AA, BB, CC],
    onSecond: (T, A, AA) => Rep[Option[Boolean]],
    extraQueryOps: QueryJoinTwo[A, B, AA, BB] => QueryJoinTwo[A, B, AA, BB]
      = (query: QueryJoinTwo[A, B, AA, BB]) => query
  ):
  QueryJoinTwo[A, B, AA, BB]= {
    val queryWithoutExtra = slickQuery
      .join(otherFirst.getSlickQuery).on((mine, theirs) => onFirst(mine, theirs))
      .join(otherSecond.getSlickQuery).on((mineAndFirst, second) => onSecond(mineAndFirst._1, mineAndFirst._2, second))
      .filter(f =>
        getDefaultFilters(f._1._1) && otherFirst.getDefaultFilters(f._1._2) && otherSecond.getDefaultFilters(f._2)
      )
    extraQueryOps(queryWithoutExtra)
  }

  /**
    * Build a left join query between two daos
    * @param other other DAO to join to
    * @param on lambda filter function to specify "on" clause for join
    * @param extraQueryOps extra where clause
    * @tparam A type of other slick table
    * @tparam B type of other slick model
    * @tparam C type of other idtype
    * @return query to do left join, "other" piece is option to return
    */
  def leftJoinQuery[A <: DAOTable[B, C], B <: IdModel[C], C <: IDType]
  (
    other: DAOQuery[A, B, C],
    on: (T, A) => Rep[Option[Boolean]],
    extraQueryOps: QueryLeftJoin[A, B] => QueryLeftJoin[A, B] = (query: QueryLeftJoin[A, B]) => query
  ):
  QueryLeftJoin[A, B] = {
    extraQueryOps(
      slickQuery.joinLeft(other.getSlickQuery).on((mine, theirs) => on(mine, theirs))
        .filter(f => getDefaultFilters(f._1) && f._2.flatMap(theirs => other.getDefaultFilters(theirs)))
    )
  }

  /**
    * Build a read query.  Most query functions will use this as a basis for building their queries so default
    * filters get used throughout.  The only exception is joins.
    * @return
    */
  def readQuery: QueryWithFilter = {
    slickQuery.filter(q => getDefaultFilters(q))
  }

  /**
    * Build an update query.  Return id of object
    * @param id object id to update
    * @param input new object to store
    * @param ec
    * @return
    */
  def updateQuery(id: I, input: T#TableElementType)(implicit ec: ExecutionContext):
  DBIOAction[I, NoStream, Effect.Write] = {
    idEquals(readQuery, id)
      .update(input)
      .map(rowsAffected => id)
  }

  /**
    * Build a delete query
    * @param id object id to delete
    * @return
    */
  def deleteQuery(id: I): DBIOAction[Int, NoStream, Effect.Write] = {
    idEquals(readQuery, id)
      .delete
  }

  /**
    * Build a read query based on an id
    * @param id object id
    * @return
    */
  def readByIdQuery(id: I): QueryWithFilter = {
    idEquals(readQuery, id)
  }

  /**
    * Build a read query for a set of ids
    * @param id set of ids
    * @return
    */
  def readByIdQuery(id: Set[I]): QueryWithFilter = {
    idInSet(readQuery, id.toSeq)
  }

}

/**
  * Functions for handling queries against the different id types
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  * @tparam I id type (option long and uuid)
  */
trait IdQuery[T <: DAOTable[V, I], V <: IdModel[I], I <: IDType]
  extends DefaultFilter[T, V, I] {
  protected val driver: AiqDriver
  protected val context: Context
  import driver.api._ // scalastyle:ignore

  /**
    * Query to filter id = something
    * @param query existing query
    * @param id id to filter on
    * @return
    */
  def idEquals(query: QueryWithFilter, id: I): QueryWithFilter

  /**
    * Query to filter id in (some seq)
    * @param query existing query
    * @param ids ids to filter on
    * @return
    */
  def idInSet(query: QueryWithFilter, ids: Seq[I]): QueryWithFilter

  /**
    * Retrieve ID column from query
    * @return
    */
  def idMap: Query[Rep[I], I, Seq]

  /**
    * Retrieve sequence of ids from sequence of rows
    * @param input sequence of rows
    * @return
    */
  def idsAsSeq(input: Seq[V]): Seq[I]

  /**
    * Create a query for creating objects.  This query depends on the id type since MySQL can only return ids of
    * autoincs
    * @param input new row
    * @param ec
    * @return
    */
  def createQuery(input: V)(implicit ec: ExecutionContext):
  DBIOAction[I, NoStream, Effect.Read with Effect.Write]
}

/**
  * Implementation of handling queries with option[long] id
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  */
trait DAOLongIdQuery[T <: DAOTable[V, DbLongOptID], V <: IdModel[DbLongOptID]]
  extends IdQuery[T, V, DbLongOptID]{
  protected val driver: AiqDriver
  protected val context: Context
  import driver.api._ // scalastyle:ignore

  protected implicit val dbOptLongJdbcType = new OptLongJdbcType

  /**
    * Filter id equals DbLongOptID
    * @param query existing query
    * @param id id to filter on
    * @return
    */
  def idEquals(query: QueryWithFilter, id: DbLongOptID): QueryWithFilter = {
    query.filter(_.id === id)
  }

  /**
    * Filter id in seq of DbLongOptID
    * @param query existing query
    * @param ids ids to filter on
    * @return
    */
  def idInSet(query: QueryWithFilter, ids: Seq[DbLongOptID]): QueryWithFilter = {
    query.filter(_.id inSet(ids))
  }

  /**
    * Retrieve ID column from query
    * @return
    */
  def idMap: Query[Rep[DbLongOptID], DbLongOptID, Seq] = slickQuery.map(_.id)

  /**
    * Retrieve seq of ids from resultset
    * @param input sequence of rows
    * @return
    */
  def idsAsSeq(input: Seq[V]): Seq[DbLongOptID] = input.map(_.id)

  /**
    * Generate create query
    * @param input new row
    * @param ec
    * @return autoinc id
    */
  def createQuery(input: V)(implicit ec: ExecutionContext):
  DBIOAction[DbLongOptID, NoStream, Effect.Read with Effect.Write] = {
    slickQuery returning slickQuery.map(_.id) += input
  }
}

/**
  * Implementation of handling queries with UUID
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  */
trait DAOUUIDQuery[T <: DAOTable[V, DbUUID], V <: IdModel[DbUUID]]
  extends IdQuery[T, V, DbUUID]{
  protected val driver: AiqDriver
  protected val context: Context
  import driver.api._ // scalastyle:ignore

  import slick.driver.MySQLDriver.DriverJdbcType // scalastyle:ignore
  implicit val dbUuidJdbcType = new UUIDJdbcType

  /**
    * Filter id equals UUID
    * @param query existing query
    * @param id id to filter on
    * @return
    */
  def idEquals(query: QueryWithFilter, id: DbUUID): QueryWithFilter = {
    query.filter(_.id === id)
  }

  /**
    * Filter id in seq of UUID
    * @param query existing query
    * @param ids ids to filter on
    * @return
    */
  def idInSet(query: QueryWithFilter, ids: Seq[DbUUID]): QueryWithFilter = {
    query.filter(_.id inSet(ids))
  }

  /**
    * Retrieve ID column from query
    * @return
    */
  def idMap: Query[Rep[DbUUID], DbUUID, Seq] = slickQuery.map(_.id)

  /**
    * Retrieve seq of ids from resultset
    * @param input sequence of rows
    * @return
    */
  def idsAsSeq(input: Seq[V]): Seq[DbUUID] = input.map(_.id)

  /**
    * Generate create query
    * @param input new row
    * @param ec
    * @return UUID
    */
  def createQuery(input: T#TableElementType)(implicit ec: ExecutionContext):
  DBIOAction[DbUUID, NoStream, Effect.Read with Effect.Write] = {
    for {
      insert <- slickQuery += input
      id <- DBIO.successful(input.id)
    } yield id
  }
}
