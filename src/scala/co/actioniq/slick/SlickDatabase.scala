package co.actioniq.slick

import co.actioniq.config.Conf
import co.actioniq.functional.{AiqLogger, HasStartStop, Singleton, StartStopProvider}
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.JdbcBackend.Database

object SlickDatabase {

  // ensure HikariCP can be loaded
  Class.forName("slick.jdbc.hikaricp.HikariCPJdbcDataSource")

  private val hikariValidationConfigs = """
       |connectionInitSql = "/* ping */ SELECT 1"
       |initializationFailFast = true
     """.stripMargin

  def create(poolName: String, config: Config): Database = {
    val poolConfig = config.getConfig(poolName)
    create(
      poolName = poolName,
      url = poolConfig.getString("url"),
      connectionPoolType = poolConfig.getString("connectionPool"),
      numConnections = poolConfig.getInt("maxConnections"),
      queueSize = poolConfig.getInt("queueSize"),
      timeoutSecs = poolConfig.getInt("connectionTimeout"),
      keepAliveConnection = false,
      maxLifeTimeSecs = poolConfig.getInt("maxLifetime")
    )
  }

  def create(
    poolName: String,
    url: String,
    connectionPoolType: String,
    numConnections: Int,
    queueSize: Int,
    timeoutSecs: Int,
    keepAliveConnection: Boolean,
    maxLifeTimeSecs: Int
  ): Database = connectionPoolType match {
    case "disabled" =>
      Database.forConfig(poolName, ConfigFactory.parseString(s"""
         |${poolName} = {
         |url = "$url&connectTimeout=${timeoutSecs}&maxReconnects=30"
         |numThreads = $numConnections
         |queueSize = $queueSize
         |keepAliveConnection = $keepAliveConnection
         |connectionPool = "disabled"
         |$hikariValidationConfigs
         |}
      """.stripMargin))
    case "HikariCP" =>
      Database.forConfig(poolName, ConfigFactory.parseString(
      s"""
         |${poolName} = {
         |url = "$url"
         |numThreads = $numConnections
         |queueSize = $queueSize
         |keepAliveConnection = $keepAliveConnection
         |connectionPool = "HikariCP"
         |maxConnections = $numConnections
         |maxLifetime = ${maxLifeTimeSecs * 1000}
         |idleTimeout = ${maxLifeTimeSecs * 1000}
         |connectionTimeout = ${timeoutSecs * 1000}
         |validationTimeout = ${timeoutSecs * 1000}
         |$hikariValidationConfigs
         |}
      """.stripMargin))
    case x =>
      throw new IllegalArgumentException(s"Pool type $x is not supported")
  }

  /**
    * Singleton instance of a slick [[Database]] for an entire application.
    */
  private val database = new Singleton[Database]()

  /**
    * Provides an instance of a JDBC-compatible slick database driver.
    */
  trait SlickDriverProvider {
    def driver: AiqDriver
  }

  /**
    * Provides a properly-configured slick database object.
    * This object can be used to made CRUD operations on the
    * underlying database using `TableQuery` objects.
    */
  trait Provider extends SlickDriverProvider
    with HasStartStop
    with StartStopProvider
    with DatabaseConnectionString.Provider
    with AiqLogger {

    def db: Database
    final override lazy val driver = slickDriver(connectionString)

    // forces initialization of the db to avoid an NPE. Work around for this: http://bit.ly/2jVGB4Z
    // TODO: remove when we upgrade slick library.
    final override def start(): Unit = {
      val session = db.createSession()
      try {
        log.debug(s"Trying to initialize $connectionString")
        session.force()
        log.debug(s"Successfully initialized $connectionString")
      } catch {
        case e: Exception =>
          log.warn(s"Forcing initialization of DB caused exception $e. Ignoring.")
      } finally {
        session.close()
      }
    }

    abstract override def getStartStops: List[HasStartStop] = this :: super.getStartStops
  }

  /**
    * Production implementation of [[Provider]] that uses
    * a singleton object to ensure that only one [[Database]]
    * object exists across the application.
    */
  trait ProviderProdImpl extends Provider with DatabaseConnectionString.Provider with Conf.Provider {
    final override val db: Database = database.getOrInit(
      create("query-api-pool", config.getConfig("aiq.db"))
    )
    final override def stop(): Unit = db.close() // IMPORTANT: once this is called, the slick db object is unusable!
  }

  /**
    * Test implementation of [[Provider]] that uses
    * an inline val, so that there is a single [[Database]]
    * object per test. Note that unlike the prod implementation,
    * the `stop` method is not `final`, allowing tests to
    * perform additional cleanup tasks.
    */
  trait ProviderTestImpl extends Provider
    with DatabaseConnectionString.Provider {
    final override lazy val db: Database = {
      val conn = AiqConnection(connectionString)
      SlickDatabase.create(
        poolName = conn.databaseName,
        url = connectionString,
        connectionPoolType = "HikariCP",
        numConnections = 5,
        queueSize = 100,
        timeoutSecs = 20,
        keepAliveConnection = true,
        maxLifeTimeSecs = 600
      )
    }

    // IMPORTANT: once this is called, the slick db object is unusable!
    final override def stop(): Unit = {
      db.close()
    }
  }

  /**
    * Maps driver names in URLs to the appropriate Slick driver classes.
    */
  private def slickDriver(connectionString: String): AiqDriver = {
    AiqConnection(connectionString).databaseTypeFromJdbcUrl match {
      case Some("h2") => AiqH2Driver
      case Some("mysql") => AiqMySQLDriver
      case _ => throw new IllegalArgumentException(s"no slick driver defined for JDBC url:$connectionString")
    }
  }
}

object DatabaseConnectionString {
  /**
    * Provides a JDBC database connection string (aka db url) for accessing a database.
    */
  trait Provider {
    def connectionString: String
  }

  /**
    * Implementation of [[Provider]] that gets the connection string from the AIQ config file.
    */
  trait ProviderDefaultImpl extends Provider with Conf.Provider {
    protected def connectionStringConfigPath: String
    final override def connectionString: String = {
      config.getString(connectionStringConfigPath)
    }
  }
}
