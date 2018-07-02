package com.gabry.beecache.protocol

/**
  * Created by gabry on 2018/6/28 18:03
  */
trait EntityMessage extends Message{
  def key:String
}