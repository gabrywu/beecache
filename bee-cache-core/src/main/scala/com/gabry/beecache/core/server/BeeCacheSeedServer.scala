package com.gabry.beecache.core.server

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.gabry.beecache.core.Node
import com.gabry.beecache.core.registry.RegistryFactory
import com.typesafe.config.ConfigFactory

/**
  * Created by gabry on 2018/7/2 16:48
  */
object BeeCacheSeedServer {
  def main(args: Array[String]): Unit = {
    val defaultConfig = ConfigFactory.load()
    val registry = RegistryFactory.getRegistry(defaultConfig).getOrElse(null)
    println(s"registry=$registry")

    val seeds = if( registry != null ){
      try{
        registry.connect()
        registry.getNodesByType("seed").map(node=>"\""+node.anchor +"\"")
      }catch {
        case exception:Exception=>
          println(s"registry has errors: $exception")
          Array.empty[String]
      }
    }else
      Array.empty[String]

    val config = if( seeds.nonEmpty )
      defaultConfig.getConfig("seed")
        .withFallback(ConfigFactory.parseString(s"akka.cluster.seed-nodes=[${seeds.mkString(",")}]"))
        .withFallback(ConfigFactory.load())
    else
      defaultConfig.getConfig("seed").withFallback(ConfigFactory.load())

    val clusterName = config.getString("clusterNode.cluster-name")
    val system = ActorSystem(clusterName, config)
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    if(registry!=null){
      println(s"registry node ${Node("seed",cluster.selfAddress.toString)}")
      registry.registerNode(Node("seed",cluster.selfAddress.toString))
    }
    system.registerOnTermination{
      if(registry!=null){
        registry.disConnect()
      }
    }
  }
}
