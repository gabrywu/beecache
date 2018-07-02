package com.gabry.beecache.core.server

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
  * Created by gabry on 2018/7/2 16:48
  */
object BeeCacheSeedServer {
  def main(args: Array[String]): Unit = {

    val seedConfig = ConfigFactory.load("default/seed.conf")
    println(s"seecConfig=$seedConfig")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=2551")
      .withFallback(ConfigFactory.parseString("akka.cluster.roles=[\"seed\"]"))
      .withFallback(ConfigFactory.load())

    val clusterName = config.getString("clusterNode.cluster-name")
    val system = ActorSystem(clusterName, config)

  }
}
