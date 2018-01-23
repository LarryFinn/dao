package co.actioniq.slick.dao

import java.util.UUID

import co.actioniq.config.Conf
import co.actioniq.functional.Singleton
import net.ceedubs.ficus.Ficus._

object DAODatabase {
  trait WriteConfigProvider {
    def getWriteConfigDbName: String
  }

  trait WriteConfigProviderProdImpl extends Conf.Provider {
    def getWriteConfigDbName: String = config.as[Option[String]]("aiq.write_config.db_name")
      .getOrElse("write_config")
  }

  trait WriteConfigProviderTestImpl {
    private lazy val writeConfigDbName = new Singleton[String].getOrInit("WriteConfigDbSpecification_" +
      UUID.randomUUID.toString.replaceAll("-", "_"))
    def getWriteConfigDbName: String = writeConfigDbName
  }

}
