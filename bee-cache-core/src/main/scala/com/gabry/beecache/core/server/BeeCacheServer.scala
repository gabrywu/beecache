package com.gabry.beecache.core.server

import akka.actor.{ActorPath, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import com.gabry.beecache.core.actor.BeeCacheActor
import com.gabry.beecache.core.extractor.BeeCacheMessageExtractor
import com.gabry.beecache.core.registry.RegistryFactory
import com.gabry.beecache.protocol.constant.Constants
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * Created by gabry on 2018/6/27 15:30
  */
object BeeCacheServer {
  private val log = LoggerFactory.getLogger(BeeCacheServer.getClass)
  def main(args: Array[String]): Unit = {
    val port = args.headOption.map(_.toInt).getOrElse(0)

    val defaultConfig = ConfigFactory.load()
    val registry = RegistryFactory.getRegistryOrDefault(defaultConfig)
    try{
      registry.connect()
      val seeds = registry.getNodesByType("seed").map(node=>ActorPath.fromString(node.anchor).address).toList
      if(seeds.nonEmpty){

        val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
          .withFallback(defaultConfig.getConfig("server"))
          .withFallback(ConfigFactory.parseString(s"akka.cluster.seed-nodes=[]"))
          .withFallback(defaultConfig)

        val clusterName = config.getString("clusterNode.cluster-name")
        val system = ActorSystem(clusterName, config)
        val cluster = Cluster(system)
        cluster.joinSeedNodes(seeds)

        val numberOfShards = system.settings.config.getInt("server.number-of-shards")

        val levelDbStore = system.actorOf(Props[SharedLeveldbStore],"store")
        SharedLeveldbJournal.setStore(levelDbStore,system)

        ClusterSharding(system).start(
          typeName = Constants.ENTITY_TYPE_NAME,
          entityProps = BeeCacheActor.props,
          settings = ClusterShardingSettings(system),
          messageExtractor = BeeCacheMessageExtractor(numberOfShards))
      }else{
        log.error("Cannot find seed node, you must start it first")
      }
    }catch {
      case exception:Exception =>
        log.error("Cannot connect to registry",exception)
    }finally {
      registry.disConnect()
    }
    log.info("BeeCache server started")
  }
}
