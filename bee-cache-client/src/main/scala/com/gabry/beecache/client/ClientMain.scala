package com.gabry.beecache.client

import com.gabry.beecache.protocol.BeeCacheData
import com.typesafe.config.ConfigFactory

/**
  * Created by gabry on 2018/7/2 13:44
  */
object ClientMain {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val client = new BeeCacheClient(config)
    client.initialize()
    client.set(BeeCacheData("123",Some("this is value"),18000)).foreach{ res =>
      if(res){
        val one = client.get("123")
        println(s"one=$one")
        client.delete("123")
        val one1 = client.get("123")
        println(s"one1=$one1")
      }
    }

    client.destroy()
  }
}
