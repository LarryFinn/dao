package co.actioniq.slick.dao

import com.twitter.bijection.twitter_util.UtilBijections
import com.twitter.util.Future

import scala.concurrent.ExecutionContext

/**
  * Sometimes you feel like a twitter future, sometimes you don't
  */
object Implicit {
  implicit def scalaToTwitterConverter[A](scalaFuture: scala.concurrent.Future[A])(implicit ec: ExecutionContext):
  Future[A] = {
    val bijection = UtilBijections.twitter2ScalaFuture[A]
    bijection.invert(scalaFuture)
  }

  implicit def twitterToScalaConverter[A](future: Future[A])(implicit ec: ExecutionContext):
  scala.concurrent.Future[A] = {
    val bijection = UtilBijections.twitter2ScalaFuture[A]
    bijection.inverse.invert(future)
  }
}
