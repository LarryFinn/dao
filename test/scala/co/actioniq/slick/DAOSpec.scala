package co.actioniq.slick

import java.util.UUID
import java.util.concurrent.TimeUnit

import co.actioniq.slick.dao.{DAOLongIdQuery, DAOUUIDQuery, DbLongOptId, DbUUID, DefaultFilter, FormValidatorMessageSeq, H2DAO, H2DAOTable, IdModel, IdType, JdbcTypeImplicits}
import co.actioniq.slick.logging.NoopBackend
import co.actioniq.slick.OptionCompareOption._
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import slick.jdbc.{GetResult, H2Profile, JdbcProfile, PositionedParameters, SetParameter}
import slick.jdbc.H2Profile.api._
import slick.lifted.{Rep, TableQuery, Tag}
import com.twitter.util.{Await => TAwait}
import slick.dbio.DBIOAction

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class DAOSpec extends Specification with Mockito {
  "DbUUID" should {
    "handle conversions" in new TestScope {
      val randomUuid = UUID.randomUUID().toString
      val test = DbUUID(randomUuid)
      test.toString mustEqual randomUuid
      implicit val getSupplierResult = GetResult(r => r.nextBytes())
      val hexQ = sql"select CAST(REPLACE('#$randomUuid', '-','') as binary)".as[(Array[Byte])]
      val result = scala.concurrent.Await.result(db.run(hexQ), Duration(20, TimeUnit.SECONDS))
      result.head mustEqual test.binValue
    }
  }

  "DbLongOptId DAO" should {
    "generate id queries" in new TestScope {
      val team = TAwait.result(teamDao.readById(DbLongOptId(1)))
      team.get.name mustEqual "mets"
      val teams = TAwait.result(teamDao.readById(Set(DbLongOptId(1), DbLongOptId(2))))
      teams.size mustEqual 2
      teams.head.name mustEqual "mets"
      teams.tail.head.name mustEqual "astros"
    }
    "create and return id" in new TestScope {
      val teamToInsert = Team(DbLongOptId(None), "Yanks")
      val id = TAwait.result(teamDao.create(teamToInsert))
      id.get mustEqual 4L
    }
    "create and return multiple ids" in new TestScope {
      val yanks = Team(DbLongOptId(None), "Yanks")
      val dodgers = Team(DbLongOptId(None), "Dodgers")
      val ids = TAwait.result(teamDao.create(Seq(yanks, dodgers)))
      ids must contain(DbLongOptId(4L), DbLongOptId(5L))
    }
    "update by id" in new TestScope {
      val team = TAwait.result(teamDao.readById(DbLongOptId(1))).get
      val updateAndRead = teamDao.updateAndRead(team.copy(name = "Red Sox"))
      val newTeam = TAwait.result(updateAndRead)
      newTeam.id.get mustEqual 1L
      newTeam.name mustEqual "Red Sox"
    }
    "delete by id" in new TestScope {
      TAwait.result(teamDao.delete(DbLongOptId(1)))
      TAwait.result(teamDao.readById(DbLongOptId(1))) must beNone
      TAwait.result(teamDao.readById(DbLongOptId(2))) must beSome
    }
  }

  "DbUUID DAO" should {
    "generate id queries" in new TestScope {
      val player = TAwait.result(playerDao.readById(larryId))
      player.get.name mustEqual "larry"
      val players = TAwait.result(playerDao.readById(Set(larryId, harryId)))
      players.size mustEqual 2
      players.map(_.name) must contain("harry", "larry")
    }
    "create and return id" in new TestScope {
      val playerId = DbUUID.randomDbUUID
      val playerToInsert = Player(playerId, 3L, "Mary")
      val id = TAwait.result(playerDao.create(playerToInsert))
      id mustEqual playerId
    }
    "create and return multiple ids" in new TestScope {
      val maryId = DbUUID.randomDbUUID
      val zarryId = DbUUID.randomDbUUID
      val marry = Player(maryId, 3L, "Mary")
      val zarry = Player(zarryId, 2L, "Zarry")
      val ids = TAwait.result(playerDao.create(Seq(marry, zarry)))
      ids must contain(maryId, zarryId)
    }
    "update by id" in new TestScope {
      val player = TAwait.result(playerDao.readById(larryId)).get
      val updateAndRead = playerDao.updateAndRead(player.copy(name = "Mary"))
      val newPlayer = TAwait.result(updateAndRead)
      newPlayer.id mustEqual larryId
      newPlayer.name mustEqual "Mary"
      newPlayer.teamId mustEqual 1L
    }
    "delete by id" in new TestScope {
      TAwait.result(playerDao.delete(larryId))
      TAwait.result(playerDao.readById(larryId)) must beNone
      TAwait.result(playerDao.readById(harryId)) must beSome
    }
  }

  "DAO Joins" should {
    "handle a join monad" in new TestScope  {
      playerDao.queryJoinWithMonad() mustEqual
        s"""select x2."id", x2."team_id", x2."name", x3."id", x3."name" from
           | "player" x2, "team" x3 where x3."id" = x2."team_id"""".stripMargin.replaceAll("\n", "")
    }
    "handle a join explicit" in new TestScope  {
      playerDao.queryJoinExplicit() mustEqual
        s"""select x2."id", x2."team_id", x2."name", x3."id", x3."name" from "player" x2,
           | "team" x3 where x3."id" = x2."team_id"""".stripMargin.replaceAll("\n", "")
    }
    "handle a join explicit with default filter" in new TestScope  {
      larryPlayerDao.queryJoinExplicit() mustEqual
        s"""select x2."id", x2."team_id", x2."name", x3."id", x3."name" from "player" x2, "team" x3 where
           | (x2."name" = 'larry') and (x3."id" = x2."team_id")""".stripMargin.replaceAll("\n", "")
    }
    "handle a join explicit with two default filters" in new TestScope  {
      doubleLarryPlayerDao.queryJoinExplicit() mustEqual
        s"""select x2."id", x2."team_id", x2."name", x3."id", x3."name" from "player" x2, "team" x3 where
           | ((x2."name" = 'larry') and (x3."name" = 'larry')) and (x3."id" = x2."team_id")""".stripMargin.replaceAll("\n", "")
    }
  }

  trait TestScope extends SlickScope with Team.Provider with Player.Provider {
    implicit val logger = new NoopBackend {}
    val teamDao = new TeamDao(
      db,
      teamSlick
    )
    val playerDao = new PlayerDAO(
      db,
      playerSlick,
      teamDao
    )
    val larryPlayerDao = new PlayerDAO(
      db,
      playerSlick,
      teamDao
    ) with FilterLarry[PlayerTable, Player, DbUUID]
    val larryTeamDao = new TeamDao(
      db,
      teamSlick
    ) with FilterLarry[TeamTable, Team, DbLongOptId]
    val doubleLarryPlayerDao = new PlayerDAO(
      db,
      playerSlick,
      larryTeamDao
    ) with FilterLarry[PlayerTable, Player, DbUUID]

    implicit object SetUUID extends SetParameter[DbUUID] {
      def apply(v: DbUUID, pp: PositionedParameters) {
        pp.setBytes(v.binValue)
      }
    }

    protected val sqlMode = sqlu"SET MODE MySQL"
    protected val createTeam = sqlu"""
      CREATE TABLE `team` (
       `id` bigint auto_increment,
       `name` varchar(255) NOT NULL,
        PRIMARY KEY (`id`)
      )
    """
    protected val mets = sqlu"""
      insert into team(name) values ('mets')
    """
    protected val astros = sqlu"""
      insert into team(name) values ('astros')
    """
    protected val braves = sqlu"""
      insert into team(name) values ('braves')
    """
    protected val createPlayer = sqlu"""
      CREATE TABLE `player` (
       `id` binary(16) NOT NULL,
       `team_id` bigint not null,
       `name` varchar(255) NOT NULL,
        PRIMARY KEY (`id`)
      )
    """
    protected val larryId = DbUUID.randomDbUUID
    protected val larry = sqlu"""
      insert into player
      (id, team_id, name)
      values($larryId, 1, 'larry')
    """
    protected val harryId = DbUUID.randomDbUUID
    protected val harry = sqlu"""
      insert into player
      (id, team_id, name)
      values($harryId, 1, 'harry')
    """
    protected val barryId = DbUUID.randomDbUUID
    protected val barry = sqlu"""
      insert into player
      (id, team_id, name)
      values($barryId, 1, 'barry')
    """
    scala.concurrent.Await.result(
      db.run(
        DBIO.seq(
          sqlMode,
          createTeam,
          createPlayer,
          mets,
          astros,
          braves,
          larry,
          harry,
          barry
        )
      ),
      Duration(20, TimeUnit.SECONDS)
    )
  }

  case class Team(
    override val id: DbLongOptId,
    name: String
  ) extends IdModel[DbLongOptId] with JdbcTypeImplicits.h2JdbcTypeImplicits.DbImplicits

  class TeamTable(tag: Tag)
    extends H2DAOTable[Team, DbLongOptId](tag, "team") with NameTable {

    override def id: Rep[DbLongOptId] = column[DbLongOptId]("id", O.AutoInc)
    override def name: Rep[String] = column[String]("name")

    override def * = ( // scalastyle:ignore
      id,
      name
    ) <> (Team.tupled, Team.unapply)
  }

  object Team {
    trait Provider {
      private val creator = (tag: Tag) => new TeamTable(tag)
      val teamSlick = new TableQuery[TeamTable](creator)
    }
    def tupled = (Team.apply _).tupled // scalastyle:ignore
  }

  class TeamDao(
    override val db: DBWithLogging,
    override val slickQuery: TableQuery[TeamTable]
  ) extends H2DAO[TeamTable, Team, DbLongOptId]
    with NoopBackend
    with DAOLongIdQuery[TeamTable, Team, H2Profile] {

    def readByIdQueryStatement(id: DbLongOptId): String = readByIdQuery(id).result.statements.head

    override protected implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    override protected def addCreateTransaction(id: DbLongOptId, input: Team): Unit = {}

    override protected def addUpdateTransaction(id: DbLongOptId, input: Team, original: Team): Unit = {}

    override protected def addDeleteTransaction(id: DbLongOptId, original: Team): Unit = {}

    override def nameSingle: String = ???

    override def validateCreate(
      input: Team
    )(implicit ec: ExecutionContext): DBIOAction[FormValidatorMessageSeq, NoStream, Effect.Read] = {
      DBIOAction.successful(FormValidatorMessageSeq())
    }

    override def validateUpdate(
      input: Team,
      original: Team
    )(implicit ec: ExecutionContext): DBIOAction[FormValidatorMessageSeq, NoStream, Effect.Read] = {
      DBIOAction.successful(FormValidatorMessageSeq())
    }
  }


  case class Player(
    override val id: DbUUID,
    teamId: Long,
    name: String
  ) extends IdModel[DbUUID] with JdbcTypeImplicits.h2JdbcTypeImplicits.DbImplicits

  class PlayerTable(tag: Tag)
    extends H2DAOTable[Player, DbUUID](tag, "player") with NameTable {

    override def id: Rep[DbUUID] = column[DbUUID]("id")
    def teamId: Rep[Long] = column[Long]("team_id")
    override def name: Rep[String] = column[String]("name")

    override def * = ( // scalastyle:ignore
      id,
      teamId,
      name
    ) <> (Player.tupled, Player.unapply)

  }

  object Player {
    trait Provider {
      private val creator = (tag: Tag) => new PlayerTable(tag)
      val playerSlick = new TableQuery[PlayerTable](creator)
    }
    def tupled = (Player.apply _).tupled // scalastyle:ignore
  }

  class PlayerDAO(
    override val db: DBWithLogging,
    override val slickQuery: TableQuery[PlayerTable],
    val teamDao: TeamDao
  ) extends H2DAO[PlayerTable, Player, DbUUID]
    with NoopBackend
    with DAOUUIDQuery[PlayerTable, Player, H2Profile] {

    override protected implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    override protected def addCreateTransaction(id: DbUUID, input: Player): Unit = {}

    override protected def addUpdateTransaction(id: DbUUID, input: Player, original: Player): Unit = {}

    override protected def addDeleteTransaction(id: DbUUID, original: Player): Unit = {}

    override def nameSingle: String = ???

    override def validateCreate(
      input: Player
    )(implicit ec: ExecutionContext): DBIOAction[FormValidatorMessageSeq, NoStream, Effect.Read] = {
      DBIOAction.successful(FormValidatorMessageSeq())
    }

    override def validateUpdate(
      input: Player,
      original: Player
    )(implicit ec: ExecutionContext): DBIOAction[FormValidatorMessageSeq, NoStream, Effect.Read] = {
      DBIOAction.successful(FormValidatorMessageSeq())
    }

    def queryJoinWithMonad(): String = {
      val q = for {
        player <- readQuery
        team <- teamDao.readQuery if optLongCompare(team.id) equalsLong player.teamId
      } yield (player, team)
      q.result.statements.toList.head
    }

    def queryJoinExplicit(): String = {
      applyDefaultFilters(
        getSlickQuery
          .join(teamDao.getSlickQuery)
          .on((player, team) => optLongCompare(team.id) equalsLong player.teamId),
        teamDao
      ).result.statements.toList.head
    }
  }

  trait NameTable {
    def name: slick.lifted.Rep[String]
  }

  trait FilterLarry[T <: H2DAOTable[V, I]
    with NameTable, V <: IdModel[I], I <: IdType]
    extends DefaultFilter[T, V, I, H2Profile] {
    protected val profile: JdbcProfile
    protected val name: String = "larry"
    import profile.api._ // scalastyle:ignore

    addDefaultFilter(t => t.name === name)
  }
}
