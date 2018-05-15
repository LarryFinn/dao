package co.actioniq.slick

import java.util.Properties

import co.actioniq.functional.AiqLogger
import co.actioniq.slick.impl.dao.DAODatabase.WriteConfigProvider
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion

object SchemaMigrator extends AiqLogger {

  val defaultProps = new Properties()
  defaultProps.setProperty("flyway.placeholders.write_config_database", "write_config")
  defaultProps.setProperty("flyway.placeholders.query_api_database", "query_api")

  /**
    * Provides an object that will automatically migrate the
    * system between subsequent versions of the database schema.
    */
  trait Provider extends WriteConfigProvider {
    val props = new Properties(defaultProps)
    defaultProps.setProperty("flyway.placeholders.write_config_database", getWriteConfigDbName)
    // Temporarily enable for running 004.5 migration from SORM.  Should disable later on after all envs have it
    defaultProps.setProperty("flyway.outOfOrder", "true")
    final def validateSchema(
      connectionString: String
    ): Unit = SchemaMigrator.validateSchema(connectionString, defaultProps)
    final def migrateSchema(
      connectionString: String
    ): Unit = SchemaMigrator.migrateSchema(connectionString, defaultProps)
    final def migrateSchema(
      connectionString: String,
      properties: Properties
    ): Unit = SchemaMigrator.migrateSchema(connectionString, properties)
  }

  def validateSchema(connectionString: String, props: Properties): Unit = {
    val migrator = getMigrator(connectionString, props)
    try {
      log.debug("Validating schema...")
      migrator.validate()
      log.debug("Successfully validated schema")
    } catch {
      case e: Exception =>
        log.error(
        "Migration missing. " +
          "If you are on your laptop, please run ./aiq run schema-migration . " +
          "If you are on an environment, please run " +
          "java -Dconfig.file=/aiq/etc/configs/migration.conf -jar /build/dist/field-tools.jar schema-migration",
          e
        )
        throw e
    }
  }

  protected def getMigrator(connectionString: String, props: Properties): Flyway = {
    log.debug(s"initializing schema using connection string: $connectionString")

    val migrator = new Flyway()

    // IMPORTANT: because I'm dealing with an existing database,
    // *and* I don't want to manage (at least not yet) the entire
    // db with flyway, I have to tell flyway:
    // 1. initialize the "schema_version" table to the baseline version (if the
    // "schema_version" table doesn't exist)
    // 2. what the baseline version is (I define it as "001")
    // once that's done, the first schema I'll actually define will have to be version 002
    migrator.setBaselineVersion(MigrationVersion.fromVersion("001"))
    migrator.setBaselineOnMigrate(true)
    migrator.configure(props)

    // NOTE: username and password are inferred from the connection string
    migrator.setDataSource(connectionString, null, null) // scalastyle:ignore
    migrator
  }

  /**
    * Migrates the schema of the specified connection string.
    */
  def migrateSchema(connectionString: String, props: Properties): Unit = {
    val migrator = getMigrator(connectionString, props)
    val appliedMigrations = migrator.migrate()

    log.debug(s"applied $appliedMigrations migrations for the database at $connectionString")
  }
}
