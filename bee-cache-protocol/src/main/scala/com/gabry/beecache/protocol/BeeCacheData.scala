package com.gabry.beecache.protocol

/**
  * Created by gabry on 2018/6/27 10:08
  */
case class BeeCacheData(key:String, var value:Option[Any], var expireTime:Long) {
  val createTime:Long = System.currentTimeMillis()
  override def toString: String = s"key[$key],value[$value],expire[$expireTime],createTime[$createTime]"
}
