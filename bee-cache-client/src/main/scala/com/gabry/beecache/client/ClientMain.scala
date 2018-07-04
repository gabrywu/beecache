package com.gabry.beecache.client

import com.typesafe.config.ConfigFactory

/**
  * Created by gabry on 2018/7/2 13:44
  */
object ClientMain {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val client = new BeeCacheClient(config)
    client.initialize()
    val one = client.get("123")
    println(s"one=$one")
    client.destroy()
  }
}
