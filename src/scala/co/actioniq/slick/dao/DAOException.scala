package co.actioniq.slick.dao

case class DAOException(
  private val message: String,
  private val cause: Throwable = None.orNull,
  klass: Option[String] = None,
  code: Option[String] = None
) extends Exception(message, cause) {
}
