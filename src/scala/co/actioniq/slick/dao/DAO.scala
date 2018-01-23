package co.actioniq.slick.dao

import co.actioniq.slick.{AiqDatabase, AiqDriver, OptLongCompare, OptionCompareOption, UUIDCompare}
import co.actioniq.slick.dao.Implicit.scalaToTwitterConverter
import co.actioniq.slick.logging.TransactionLogger
import co.actioniq.thrift.Context
import com.twitter.scrooge.ThriftStruct
import com.twitter.util.Future
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * Top of the DAO traits, this actually runs the actions in a transaction.  Most functions return a twitter future
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  * @tparam I id type (option long and uuid)
  */
trait DAO[T <: DAOTable[V, I], V <: IdModel[I], I <: IDType]
  extends DAOAction[T, V, I] {

  protected val db: AiqDatabase

  override val transactionLogger: TransactionLogger = db.transactionLogger

  import driver.api._ // scalastyle:ignore

  implicit def uuidCompare(e: Rep[DbUUID]): UUIDCompare = OptionCompareOption.uuidCompare(e)
  implicit def optLongCompare(e: Rep[DbLongOptID]): OptLongCompare = OptionCompareOption.optLongCompare(e)

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
  def readJoin[A <: DAOTable[B, C], B <: IdModel[C], C <: IDType]
  (
    other: DAO[A, B, C],
    on: (T, A) => Rep[Option[Boolean]],
    extraQueryOps: QueryJoin[A, B] => QueryJoin[A, B] = (query: QueryJoin[A, B]) => query
  ): Future[Seq[(T#TableElementType, A#TableElementType)]]
  = {
    runTransaction(readJoinAction[A, B, C](other, on, extraQueryOps))
  }

  def readJoinTwo[A <: DAOTable[B, C], B <: IdModel[C], C <: IDType,
  AA <: DAOTable[BB, CC], BB <: IdModel[CC], CC <: IDType]
  (
    otherFirst: DAO[A, B, C],
    onFirst: (T, A) => Rep[Option[Boolean]],
    otherSecond: DAO[AA, BB, CC],
    onSecond: (T, A, AA) => Rep[Option[Boolean]],
    extraQueryOps: QueryJoinTwo[A, B, AA, BB] => QueryJoinTwo[A, B, AA, BB]
      = (query: QueryJoinTwo[A, B, AA, BB]) => query
  ) (implicit ec: ExecutionContext): Future[Seq[(T#TableElementType, A#TableElementType, AA#TableElementType)]]
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
  def readLeftJoin[A <: DAOTable[B, C], B <: IdModel[C], C <: IDType]
  (other: DAO[A, B, C], on: (T, A) => Rep[Option[Boolean]]):
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
  def readWithChild[A <: DAOTable[B, C], B <: IdModel[C], C <: IDType, Z]
  (
    other: DAO[A, B, C],
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

/**
  * Common class that has an Option[Long] id with customer_id and some thrift object
  * @param toThrift function to convert from persisted to thrift
  * @param toPersisted function to convert from thrift to persisted
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  * @tparam U Thrift object type
  */
abstract class CommonDAOLongId[T <: LongIdCustomerTable[V],
V <: IdModel[DbLongOptID],
U <: ThriftStruct] (
  override val toThrift: V => U,
  override val toPersisted: (Context, U) => V
) extends DAO[T, V, DbLongOptID]
  with DAOLongIdQuery[T, V]
  with DAOActionWithThrift[T, V, U, DbLongOptID]
  with FilterContext[T, V, DbLongOptID] {

  //Easy access point for thrift functions
  val thrift = new DAOThriftHelperLong(context, driver, db, toThrift, toPersisted)

  /**
    * Helper class to perform functions consuming and returning thrift objects
    * @param context users context
    * @param driver database driver
    * @param db database
    * @param toThrift function to convert from persisted to thrift
    * @param toPersisted function to convert from thrift to persisted
    */
  class DAOThriftHelperLong
  (
    override val context: Context,
    driver: AiqDriver,
    db: Database,
    override val toThrift: V => U,
    override val toPersisted: (Context, U) => V
  )
    extends DAOThriftHelper[T, V, U, DbLongOptID]{
    type IdStruct = U {def id: Option[Long]} // scalastyle:ignore

    /**
      * Save a sequence of thrift objects in a trx and return the objects.  Handle create vs update
      * @param input sequence of thrift objects
      * @param ec
      * @return future of sequence of thrift objects stored
      */
    def save(input: Seq[IdStruct])(implicit ec: ExecutionContext): Future[Seq[U]] = {
      val ids = input.map(item => DbLongOptID(item.id)).toSet
      val actionOutput = for {
        existingIds <- dao.readByIdAction(ids)
        toCreate = input.filter(item => !existingIds.contains(item.id)).map(item => toPersisted(context, item))
        toUpdate = input.filter(item => existingIds.contains(item.id)).map(item => toPersisted(context, item))
        create <- dao.createAction(toCreate, true)
        update <- dao.updateAction(toUpdate, true, Seq())
        results <- dao.readByIdAction(ids)
      } yield results.map(toThrift)
      runTransaction(actionOutput)
    }

    /**
      * Save a single thrift object and return object.  Handle create vs update
      * @param input thrift object
      * @param ec
      * @return future of thrift object stored
      */
    def save(input: IdStruct)(implicit ec: ExecutionContext): Future[U] = {
      save(Seq(input)).map(_.head)
    }

    //Expose DAO to helper
    override val dao: CommonDAOLongId[T, V, U] = CommonDAOLongId.this
  }

}


/**
  * Common DAO class with UUID
  * @param context users context
  * @param db database
  * @param driver db driver
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  */
abstract class CommonDAOUUIDNoThrift[T <: UUIDCustomerTable[V], V <: IdModel[DbUUID]] (
  override protected val context: Context,
  protected val db: AiqDatabase,
  override protected val driver: AiqDriver
) extends DAO[T, V, DbUUID]
  with DAOUUIDQuery[T, V] with FilterContext[T, V, DbUUID]

/**
  * Common DAO class with option[long] id but no generic thrift column
  * @tparam T slick table, extends aiqtable
  * @tparam V case class to store result set rows
  */
abstract class CommonDAOLongIdNoThrift[T <: LongIdCustomerTable[V], V <: IdModel[DbLongOptID]]
 extends DAO[T, V, DbLongOptID]
  with DAOLongIdQuery[T, V] with FilterContext[T, V, DbLongOptID]

