package co.actioniq.slick.dao

case class DAOException(
  message: String = "",
  cause: Throwable = None.orNull,
  code: Option[String] = None,
  klass: Option[String] = None,
  file: Option[String] = None,
  line: Option[Int] = None
) extends Exception(message, cause)
