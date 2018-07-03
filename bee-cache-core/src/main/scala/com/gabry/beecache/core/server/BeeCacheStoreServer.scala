package com.gabry.beecache.core.server

import akka.actor.{ActorPath, ActorSystem, Props}
import akka.cluster.Cluster
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import com.gabry.beecache.core.Node
import com.gabry.beecache.core.registry.RegistryFactory
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * Created by gabry on 2018/7/3 10:41
  */
object BeeCacheStoreServer {
  private val log = LoggerFactory.getLogger(BeeCacheStoreServer.getClass)
  def main(args: Array[String]): Unit = {
    val port = args.headOption.map(_.toInt).getOrElse(0)

    val defaultConfig = ConfigFactory.load()
    val registry = RegistryFactory.getRegistryOrDefault(defaultConfig)
    try{
      registry.connect()
      val seeds = registry.getNodesByType("seed").map(node=>ActorPath.fromString(node.anchor).address).toList
      if(seeds.nonEmpty){

        val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
          .withFallback(defaultConfig.getConfig("store"))
          .withFallback(ConfigFactory.parseString(s"akka.cluster.seed-nodes=[]"))
          .withFallback(defaultConfig)

        val clusterName = config.getString("clusterNode.cluster-name")
        val system = ActorSystem(clusterName, config)
        val cluster = Cluster(system)
        cluster.joinSeedNodes(seeds)

        val levelDbStore = system.actorOf(Props[SharedLeveldbStore],"store")
        val storeNode = Node("store",levelDbStore.path.toStringWithAddress(cluster.selfAddress))
        log.info(s"Register store node: $storeNode")
        registry.registerNode(storeNode)
        system.registerOnTermination{
          registry.unRegisterNode(storeNode)
          registry.disConnect()
        }
      }else{
        log.error("Cannot find seed node, you must start it first")
      }
    }catch {
      case exception:Exception =>
        log.error("Cannot connect to registry",exception)
    }
    log.info("BeeCache Persistence Server started")
  }
}
