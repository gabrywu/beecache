package com.gabry.beecache.core.daemon

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern.ask
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.gabry.beecache.core.actor.BeeCacheActor
import com.gabry.beecache.core.extractor.BeeCacheMessageExtractor
import com.gabry.beecache.protocol.command.EntityCommand
import com.gabry.beecache.protocol.constant.Constants
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by gabry on 2018/6/27 15:30
  */
object BeeCacheDaemon {

  def main(args: Array[String]): Unit = {
    val port = args.headOption.map(_.toInt).getOrElse(0)

    val defaultConfig = ConfigFactory.load()

    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(defaultConfig)

    val clusterName = config.getString("clusterNode.cluster-name")
    val system = ActorSystem(clusterName, config)
    val numberOfShards = system.settings.config.getInt("server.number-of-shards")

    val levelDbStore = system.actorOf(Props[SharedLeveldbStore],"store")
    SharedLeveldbJournal.setStore(levelDbStore,system)

    ClusterSharding(system).start(
      typeName = Constants.ENTITY_TYPE_NAME,
      entityProps = BeeCacheActor.props,
      settings = ClusterShardingSettings(system),
      messageExtractor = BeeCacheMessageExtractor(numberOfShards))

    //val beeCacheRegion = ClusterSharding(system).shardRegion(Constants.ENTITY_TYPE_NAME)

//    val defaultTimeout = 3
//    implicit val timeout: Timeout = Timeout(defaultTimeout,TimeUnit.SECONDS)

  }
}
