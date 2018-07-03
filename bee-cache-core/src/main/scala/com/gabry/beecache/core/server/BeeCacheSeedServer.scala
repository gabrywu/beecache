package com.gabry.beecache.core.server

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.gabry.beecache.core.Node
import com.gabry.beecache.core.registry.RegistryFactory
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * Created by gabry on 2018/7/2 16:48
  */
object BeeCacheSeedServer {
  private val log = LoggerFactory.getLogger(BeeCacheSeedServer.getClass)
  def main(args: Array[String]): Unit = {
    val defaultConfig = ConfigFactory.load()
    val registry = RegistryFactory.getRegistryOrDefault(defaultConfig)

    val seeds = try{
        registry.connect()
        registry.getNodesByType("seed").map(node=>"\""+node.anchor +"\"")
      }catch {
        case exception:Exception =>
          log.error("cannot connect to registry",exception)
          Array.empty[String]
      }

    log.info(s"find seed node: ${seeds.mkString(",")}")
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
    val seedNode = Node("seed",cluster.selfAddress.toString)
    log.info(s"registry node $seedNode")
    registry.registerNode(seedNode)

    system.registerOnTermination{
      registry.unRegisterNode(seedNode)
      registry.disConnect()
    }
  }
}
