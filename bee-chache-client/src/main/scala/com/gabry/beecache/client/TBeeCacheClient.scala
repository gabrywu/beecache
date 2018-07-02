package com.gabry.beecache.client

import com.gabry.beecache.protocol.BeeCacheData

import scala.util.Try

/**
  * Created by gabry on 2018/7/2 10:37
  */
trait TBeeCacheDataOperator{
  def get(key:String):Try[BeeCacheData]
  def set(data:BeeCacheData):Try[Boolean]
  def setExpire(key:String,expireTime:Long):Try[Boolean]
  def delete(key:String):Try[Boolean]
  def select(key:String):Try[BeeCacheData]
}
trait TBeeCacheClient {
  /**
    * 初始化客户端
    */
  def initialize():Unit

  /**
    * 销毁客户端
    */
  def destroy():Unit
}
