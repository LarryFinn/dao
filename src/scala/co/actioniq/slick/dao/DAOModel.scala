package co.actioniq.slick.dao


/*
  This file contains traits for "models" / case classes that store recordsets from slick
 */

/**
  * Any DAO model that has an id
  * @tparam I IdType
  */
trait DAOModel[I <: IdType] {
  def id: I
}

