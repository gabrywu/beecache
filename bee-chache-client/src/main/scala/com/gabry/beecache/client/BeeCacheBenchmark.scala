package com.gabry.beecache.client

import com.gabry.beecache.protocol.BeeCacheData
import com.typesafe.config.ConfigFactory

/**
  * Created by gabry on 2018/7/3 14:20
  */
object BeeCacheBenchmark {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val client = new BeeCacheClient(config)
    client.initialize()
    val getNumbers = 0

    val start1 = System.currentTimeMillis()
    0 until getNumbers foreach{ i =>
      client.set(BeeCacheData(i.toString,Some("123"),30*60*1000))
    }
    val end1 = System.currentTimeMillis()
    println(s"set $getNumbers kv ,time is: ${end1-start1} 毫秒")

    val start = System.currentTimeMillis()
    0 until getNumbers foreach{ i =>
      val one = client.get(i.toString)
    }
    val end = System.currentTimeMillis()
    println(s"get $getNumbers kv ,time is: ${end-start} 毫秒")
    client.get("123")
    client.benchMark(10)
    Thread.sleep(100*1000)
    client.destroy()
  }
}
