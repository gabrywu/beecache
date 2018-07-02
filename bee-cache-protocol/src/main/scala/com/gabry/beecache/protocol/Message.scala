package com.gabry.beecache.protocol

/**
  * Created by gabry on 2018/6/27 10:30
  */
trait Message {
  /**
    * 消息发生的时间
    */
  final val at: Long = System.currentTimeMillis()
}
