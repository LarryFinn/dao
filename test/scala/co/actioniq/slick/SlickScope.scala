package co.actioniq.slick

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.specs2.specification.Scope
import slick.jdbc.JdbcBackend.Database

trait SlickScope extends Scope {
  val uuid = UUID.randomUUID().toString.replaceAll("-", "")
  val h2ConfigString =
    s"""
      |h2mem = {
      |  url = "jdbc:h2:mem:$uuid;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
      |  driver = org.h2.Driver
      |  keepAliveConnection = true
      |}
    """.stripMargin
  lazy val db = Database.forConfig("h2mem", ConfigFactory.parseString(h2ConfigString))

}
