package co.actioniq.slick


import co.actioniq.slick.dao.DAOTable
import slick.jdbc.JdbcProfile


trait SlickProfile extends JdbcProfile with SlickDelete { driver =>
  override val profile: SlickDelete = this.asInstanceOf[SlickDelete]
}

trait SlickDelete extends JdbcProfile { driver: JdbcProfile =>
  import scala.language.higherKinds // scalastyle:ignore
  trait API extends super.API {
    implicit def queryDeleteActionExtensionMethodsToo[C[_]](q: Query[_ <: DAOTable.Table[_, _, _], _, C]):
    DeleteActionExtensionMethods =
      createDeleteActionExtensionMethods(deleteCompiler.run(q.toNode).tree, ())
  }

  override val api: API = new API {}
}






