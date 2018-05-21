package co.actioniq.slick.dao


import co.actioniq.slick.{DBWithLogging, OptLongCompare, OptionCompareOption, UUIDCompare}
import co.actioniq.slick.dao.Implicit.scalaToTwitterConverter
import com.twitter.util.Future
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile, PostgresProfile}

import scala.concurrent.ExecutionContext

/**
  * Top of the DAO traits, this actually runs the actions in a transaction.  Most functions return a twitter future
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  * @tparam I id type (option long and uuid)
  */
trait DAO[T <: DAOTable.Table[V, I, P], V <: IdModel[I], I <: IdType, P <: JdbcProfile]
  extends DAOAction[T, V, I, P] {

  protected val db: DBWithLogging
  protected implicit val ec: ExecutionContext

  import profile.api._ // scalastyle:ignore

  implicit def uuidCompare(e: Rep[DbUUID]): UUIDCompare = OptionCompareOption.uuidCompare(e)
  implicit def optLongCompare(e: Rep[DbLongOptId]): OptLongCompare = OptionCompareOption.optLongCompare(e)

  /**
    * Wrapper for running a trx.... SOON WE CAN PUT SOME AUDIT LOGGING IN
    * @param a DBIOAction
    * @tparam R result type
    * @return result
    */
  protected def runTransaction[R](a: DBIOAction[R, NoStream, Effect.All]): Future[R] = {
    db.run(a.transactionally)
  }

  /**
    * Run a join
    * @param other other DAO to join to
    * @param on lambda filter function to specify "on" clause for join
    * @param extraQueryOps extra where clause
    * @tparam A type of other slick table
    * @tparam B type of other slick model
    * @tparam C type of other idtype
    * @return Future of Seq of (mine, theirs)
    */
  def readJoin[A <: DAOTable.Table[B, C, P], B <: IdModel[C], C <: IdType]
  (
    other: DAO[A, B, C, P],
    on: (T, A) => Rep[Option[Boolean]],
    extraQueryOps: QueryJoin[A, B] => QueryJoin[A, B] = (query: QueryJoin[A, B]) => query
  ): Future[Seq[(T#TableElementType, A#TableElementType)]]
  = {
    runTransaction(readJoinAction[A, B, C](other, on, extraQueryOps))
  }

  def readJoinTwo[A <: DAOTable.Table[B, C, P], B <: IdModel[C], C <: IdType,
  AA <: DAOTable.Table[BB, CC, P], BB <: IdModel[CC], CC <: IdType]
  (
    otherFirst: DAO[A, B, C, P],
    onFirst: (T, A) => Rep[Option[Boolean]],
    otherSecond: DAO[AA, BB, CC, P],
    onSecond: (T, A, AA) => Rep[Option[Boolean]],
    extraQueryOps: QueryJoinTwo[A, B, AA, BB] => QueryJoinTwo[A, B, AA, BB]
      = (query: QueryJoinTwo[A, B, AA, BB]) => query
  ): Future[Seq[(T#TableElementType, A#TableElementType, AA#TableElementType)]]
  = {
    runTransaction(readJoinActionTwo[A, B, C, AA, BB, CC](
      otherFirst,
      onFirst,
      otherSecond,
      onSecond,
      extraQueryOps
    ))
  }

  /**
    * Run a left join
    * @param other other DAO to join to
    * @param on lambda filter function to specify "on" clause for join
    * @tparam A type of other slick table
    * @tparam B type of other slick model
    * @tparam C type of other idtype
    * @return future of Seq of (min, option(theirs))
    */
  def readLeftJoin[A <: DAOTable.Table[B, C, P], B <: IdModel[C], C <: IdType]
  (other: DAO[A, B, C, P], on: (T, A) => Rep[Option[Boolean]]):
  Future[Seq[(T#TableElementType, Option[A#TableElementType])]]
  = {
    runTransaction(readLeftJoinAction[A, B, C](other, on))
  }

  /**
    * This function is used for "joining" the results of another DAO with this DAO.  It is used for many to many
    * relationships (usually parent to child).  It is meant to allow you to have for each row of this DAO, N results
    * from another DAO.  Example (this DAO is FlightPlanTable + FlightPlanModel):
    *
    *     readWithChild[FlightTable, FlightModel, Array[Byte], (FlightPlanModel, Seq[FlightModel])](
      other = flightDao,
      filterChildOn = (flightPlans, flightTable) => {
        flightTable.flightPlanId inSet flightPlans.map(_.id.get)
      },
      merge = (flightPlans, flights) => {
        val flightsMap = flights.groupBy(_.flightPlanId)
        flightPlans.map(plan => (plan, flightsMap.getOrElse(plan.id.get, Seq())))
      }
    )
    * @param other the other DAO (child)
    * @param filterChildOn lambda function to add a SQL filter to the child DAO.  Typical usecase is if you have
    *                      parent to child relationship, the child dao will filter on child.parent_id in (parent.ids).
    *                      The parameters are: sequence of parent objects, child table
    * @param merge lambda function to merge this DAO's results with the child DAO's results.  Typically you would
    *              want something like Seq[(ParentModel, Seq[ChildModel])]
    * @param extraQueryOps optional extra filters for this DAO
    * @tparam A other Table
    * @tparam B other Model
    * @tparam C other ID type (String, Long, Array[Byte])
    * @tparam Z return type, for example Seq[(V, Seq[B])]
    * @return
    */
  def readWithChild[A <: DAOTable.Table[B, C, P], B <: IdModel[C], C <: IdType, Z]
  (
    other: DAO[A, B, C, P],
    filterChildOn: (Seq[V], A) => Rep[Option[Boolean]],
    merge: (Seq[V], Seq[B]) => Seq[Z],
    extraQueryOps: (QueryWithFilter)=> QueryWithFilter = (query) => query
  ):
  Future[Seq[Z]] = {
    runTransaction(readWithChildAction(other, filterChildOn, merge, extraQueryOps))
  }

  /**
    * Run a read
    * @param extraQueryOps extra filters / limits
    * @return future of seq of model
    */
  def read(extraQueryOps: (QueryWithFilter)=> QueryWithFilter = (query) => query): Future[Seq[V]] = {
    runTransaction(readAction(extraQueryOps))
  }

  /**
    * Run a read by id
    * @param id object id
    * @return Future of option of model
    */
  def readById(id: I): Future[Option[V]] = {
    runTransaction(readByIdAction(id))
  }

  /**
    * Run a read by set of ids
    * @param id set of ids
    * @return future of seq of model
    */
  def readById(id: Set[I]): Future[Seq[V]] = {
    runTransaction(readByIdAction(id))
  }

  /**
    * Run a read by id requiring id to exist
    * @param id object id
    * @return future of model
    */
  def readByIdRequired(id: I): Future[V] = {
    runTransaction(readByIdRequiredAction(id))
  }

  /**
    * Run a read by set of ids requiring ids exist
    * @param id set of object ids
    * @return future of seq of models
    */
  def readByIdRequired(id: Set[I]): Future[Seq[V]] = {
    runTransaction(readByIdRequiredAction(id))
  }

  /**
    * Run a create
    * @param input model to create
    * @return future of id
    */
  def create(input: V): Future[I] = {
    runTransaction(createAction(processPreCreate(input), true))
  }

  /**
    * Run a create and return object
    * @param input model to create
    * @return future of object persisted
    */
  def createAndRead(input: V): Future[V] = {
    val actions = for {
      create <- createAction(processPreCreate(input))
      row <- readByIdAction(create).map(_.get)
    } yield processPostCreate(row)
    runTransaction(actions)
  }

  /**
    * Run a create on multiple objects in trx
    * @param input sequence of models
    * @return future of set of ids
    */
  def create(input: Seq[V]): Future[Seq[I]] = {
    runTransaction(createAction(input.map(processPreCreate), true))
  }

  /**
    * Run a create on multiple objects in trx and return objects
    * @param input seq of objects to save
    * @return future of seq of objects saved
    */
  def createAndRead(input: Seq[V]): Future[Seq[V]] = {
    val actions = for {
      create <- createAction(input.map(processPreCreate), true)
      rows <- readAction(query => idInSet(query, create))
    } yield rows.map(processPostCreate)
    runTransaction(actions)
  }

  /**
    * Run an update
    * @param input object to update
    * @return future of object id
    */
  def update(input: V): Future[I] = {
    val actions = for {
      original <- readByIdAction(input.id)
      processedInput = processPreUpdate(input, original)
      update <- updateAction(processedInput, true, original)
    } yield update
    runTransaction(actions)
  }

  /**
    * Run an update and return persisted object
    * @param input object to update
    * @return future of object persisted
    */
  def updateAndRead(input: V): Future[V] = {
    val actions = for {
      original <- updateGetOriginal(input.id)
      processedInput = processPreUpdate(input, original)
      update <- updateAction(processedInput, true, original)
      row <- readByIdAction(input.id).map(_.get)
    } yield processPostUpdate(row)
    runTransaction(actions)
  }

  /**
    * Run an update and return persisted object
    * @param id id of object to update
    * @param updateFn a function that updates the object
    * @return future of object persisted
    */
  def updateAndRead(id: I, updateFn: V => V): Future[V] = {
    val actions = for {
      updateId <- updateActionFunctional(id, toValidate = true, updateFn)
      row <- readByIdRequiredAction(updateId)
    } yield row
    runTransaction(actions)
  }

  /**
    * Run an update on a sequence and return persisted object
    * @param queryOps: (QueryWithFilter) => QueryWithFilter = (query) => query
    * @param updateFn a function that updates the object
    * @return future of object persisted
    */
  def queryAndUpdate(
    queryOps: (QueryWithFilter) => QueryWithFilter,
    updateFn: Seq[V] => Seq[V]
  ): Future[Seq[I]] = {
    val actions = for {
      rows <- readAction(queryOps)
      updatedModels = updateFn(rows)
      update <- updateAction(updatedModels, toValidate = true, rows)
    } yield update
    runTransaction(actions)
  }

  /**
    * Run an update on multiple objects in a trx
    * @param input seq of models to persist
    * @return future seq of ids
    */
  def update(input: Seq[V]): Future[Seq[I]] = {
    val ids = input.map(_.id)
    val actions = for {
      originals <- readAction(query => idInSet(query, ids))
      processedInputs = input.map { item =>
        val original = originals.find(_.id == item.id)
        processPreUpdate(item, original)
      }
      updates <- updateAction(processedInputs, true, originals)
    } yield updates
    runTransaction(actions)
  }

  /**
    * Run an update on multiple object in a trx and return the persisted objects
    * @param input seq of models to persist
    * @return future of seq of objects
    */
  def updateAndRead(input: Seq[V]): Future[Seq[V]] = {
    val ids = input.map(_.id)
    val actions = for {
      originals <- readAction(query => idInSet(query, ids))
      processedInputs = input.map { item =>
        val original = originals.find(_.id == item.id)
        processPreUpdate(item, original)
      }
      updates <- updateAction(processedInputs, true, originals)
      rows <- readAction(query => idInSet(query, ids))
    } yield rows.map(processPostUpdate)
    runTransaction(actions)
  }

  /**
    * Run a delete
    * @param inputId object id
    * @return future of number of rows updated
    */
  def delete(inputId: I): Future[Int] = {
    runTransaction(deleteAction(inputId))
  }

  /**
    * Run a delete
    * @param inputIds seq of object ids
    * @return future of number of rows updated
    */
  def delete(inputIds: Seq[I]): Future[Seq[Int]] = {
    runTransaction(deleteAction(inputIds))
  }
}

trait MySQLDAO[T <: MySQLDAOTable[V, I], V <: IdModel[I], I <: IdType] extends DAO[T, V, I, MySQLProfile]

trait PostgresDAO[T <: PostgresDAOTable[V, I], V <: IdModel[I], I <: IdType] extends DAO[T, V, I, PostgresProfile]

trait H2DAO[T <: H2DAOTable[V, I], V <: IdModel[I], I <: IdType] extends DAO[T, V, I, H2Profile]
