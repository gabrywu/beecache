package com.gabry.beecache.client.benchmark

import java.util.Optional

import akka.actor.{ActorPath, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import com.gabry.beecache.core.constant.Constants
import com.gabry.beecache.core.extractor.BeeCacheMessageExtractor
import com.gabry.beecache.core.registry.RegistryFactory
import com.gabry.beecache.protocol.command.EntityCommand
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * Created by gabry on 2018/7/3 14:20
  */
object BeeCacheBenchmark {
  private val log = LoggerFactory.getLogger(BeeCacheBenchmark.getClass)
  def main(args: Array[String]): Unit = {
    val parallel = args.headOption.map(_.toInt).getOrElse(10)
    val msgNumberPerParallel = if(args.length>1) args(1).toInt else 10000
    val config = ConfigFactory.load()
    val clusterName: String = config.getString("clusterNode.cluster-name")
    val shardingRole: String = config.getString("akka.cluster.sharding.role")
    val numberOfShards = config.getInt("server.number-of-shards")
    val system = ActorSystem(clusterName, config)
    val registry = RegistryFactory.getRegistryOrDefault(config)
    try{
      registry.connect()
      val seeds = registry.getNodesByType("seed").map(node=>ActorPath.fromString(node.anchor).address).toList
      if(seeds.nonEmpty){
        val cluster = Cluster(system)
        cluster.joinSeedNodes(seeds)
      }
      val beeCacheRegion = ClusterSharding(system).startProxy(
        typeName = Constants.ENTITY_TYPE_NAME
        ,role = Optional.of(shardingRole)
        ,messageExtractor = BeeCacheMessageExtractor(numberOfShards))
      beeCacheRegion !  EntityCommand.Get("123")

      Thread.sleep(3*1000)
      val mark = system.actorOf(Props(new BenchmarkActor(beeCacheRegion,config,parallel,msgNumberPerParallel)))
    }catch {
      case exception:Exception =>
        log.error(s"Cannot connect to Registry: $exception")
    }finally {
      registry.disConnect()
    }
  }
}
