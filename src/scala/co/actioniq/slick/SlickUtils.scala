package co.actioniq.slick

import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

object SlickUtils {

  /**
    * Recursively unfolds (opposite of "fold") initial values ''z'' producing additional values 1 step at a time using
    * function ''f''
    *
    * (generalized version of unfold here http://books.underscore.io/essential-slick/essential-slick-3.html#unfolding )
    *
    * @param z initial "zero" value
    * @param f unfolds 1 level
    * @param acc Accumulates results for recursion
    * @tparam A slick representation type of values that you are "unfolding"
    * @return all values produced by unfolding, including the initial values
    */
  def unfoldSeq[A](
    z: Seq[A],
    f: Seq[A] => DBIO[Seq[A]],
    acc: Seq[A] = Seq.empty
  )(
    implicit ec: ExecutionContext
  ): DBIO[Seq[A]] = {
    f(z).flatMap {
      case Nil => DBIO.successful(acc ++ z)
      case as: Seq[A] => unfoldSeq(as, f, acc ++ z)
    }
  }
}
