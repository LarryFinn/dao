package co.actioniq.slick.example

import co.actioniq.slick.dao.{DefaultFilter, H2DAOTable, IdModel, IdType}
import slick.jdbc.{H2Profile, JdbcProfile}

trait FilterLarry[T <: H2DAOTable[V, I]
  with NameTable, V <: IdModel[I], I <: IdType]
  extends DefaultFilter[T, V, I, H2Profile] {
  protected val profile: JdbcProfile
  protected val name: String = "larry"
  import profile.api._ // scalastyle:ignore

  addDefaultFilter(t => t.name === name)
}
