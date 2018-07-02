package com.gabry.beecache.protocol.event

import com.gabry.beecache.protocol.EntityMessage

/**
  * Created by gabry on 2018/6/28 18:05
  */
sealed trait EntityEvent extends EntityMessage
object EntityEvent{
  case class Updated(key:String) extends EntityEvent
  case class Deleted(key:String) extends EntityEvent
}