package com.gabry.beecache.protocol.exception

import com.gabry.beecache.protocol.EntityMessage

/**
  * Created by gabry on 2018/6/28 17:29
  */
abstract class BeeCacheException(exceptionMessage:String) extends Exception(exceptionMessage) with EntityMessage
case class UnknownBeeCacheException(exceptionMessage:String) extends Exception(exceptionMessage)
object EntityException{
  case class KeyNotFound(key: String ,exceptionMessage:String) extends BeeCacheException(exceptionMessage)
}