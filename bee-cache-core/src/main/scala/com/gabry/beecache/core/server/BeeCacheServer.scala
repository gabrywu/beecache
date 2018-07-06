package com.gabry.beecache.core.server

import java.util.concurrent.TimeUnit

import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Identify, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern._
import akka.persistence.journal.leveldb.SharedLeveldbJournal
import akka.util.Timeout
import com.gabry.beecache.core.actor.BeeCacheActor
import com.gabry.beecache.core.extractor.BeeCacheMessageExtractor
import com.gabry.beecache.core.registry.RegistryFactory
import com.gabry.beecache.protocol.constant.Constants
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

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
        println(s"config=$config")
        val clusterName = config.getString("clusterNode.cluster-name")
        val system = ActorSystem(clusterName, config)
        val cluster = Cluster(system)
        cluster.joinSeedNodes(seeds)

        implicit val timeout: Timeout = Timeout(config.getDuration("server.db-store-resolve-timeout").toMillis,TimeUnit.MILLISECONDS)
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher

        val stores = registry.getNodesByType("store").map(node=>ActorPath.fromString(node.anchor).address).toList
        log.info(s"Current cluster store node: ${stores.mkString(",")}")

        if(stores.nonEmpty){
          system.actorSelection( RootActorPath(stores.head) / "user" / "store" ) ? Identify(None) onComplete {
            case Success(ActorIdentity(_,Some(dbStore))) =>
              log.info(s"Set current store: $dbStore")
              SharedLeveldbJournal.setStore(dbStore, system)
              val numberOfShards = system.settings.config.getInt("server.number-of-shards")
              ClusterSharding(system).start(
                typeName = Constants.ENTITY_TYPE_NAME,
                entityProps = BeeCacheActor.props,
                settings = ClusterShardingSettings(system),
                messageExtractor = BeeCacheMessageExtractor(numberOfShards))
            case Success(ActorIdentity(_,None)) =>
              log.error(s"Cannot resolve db store")
              system.terminate()
            case Success(notActorIdentity) =>
              log.error(s"Receive unknown message: $notActorIdentity")
              system.terminate()
            case Failure(exception) =>
              log.error(s"Cannot resolve db store: $exception", exception)
              system.terminate()
          }
        }else{
          log.error("Store actor not started,you must start it first")
          system.terminate()
        }
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
