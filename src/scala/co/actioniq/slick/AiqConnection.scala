package co.actioniq.slick

case class AiqConnection(connectionString: String) {
  val databaseTypeFromJdbcUrl: Option[String] = {
    if (connectionString.startsWith("jdbc")) {
      connectionString.split(':').drop(1).headOption
    } else {
      None
    }
  }

  val databaseName: String = {
    val dbAndQs = connectionString.split("//")(1).split("/")(1)
    val indexOfQ = dbAndQs.indexOf("?")
    if (indexOfQ == -1) {
      dbAndQs
    } else {
      dbAndQs.substring(0, indexOfQ)
    }
  }
}
