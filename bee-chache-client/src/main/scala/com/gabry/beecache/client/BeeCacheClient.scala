package com.gabry.beecache.client

import java.util.Optional
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.pattern._
import akka.util.Timeout
import com.gabry.beecache.core.extractor.BeeCacheMessageExtractor
import com.gabry.beecache.protocol.{BeeCacheData, EntityMessage}
import com.gabry.beecache.protocol.command.EntityCommand
import com.gabry.beecache.protocol.constant.Constants
import com.gabry.beecache.protocol.event.EntityEvent
import com.gabry.beecache.protocol.exception.{BeeCacheException, UnknownBeeCacheException}
import com.typesafe.config.Config

import scala.concurrent.Await
import scala.util.{Success, Try}

/**
  * Created by gabry on 2018/7/2 10:48
  */
class BeeCacheClient(config:Config) extends AbstractBeeCacheClient(config) {

  private val clusterName: String = config.getString("clusterNode.cluster-name")
  private implicit val defaultTimeout: Timeout = Timeout(config.getDuration("client.request-time-out").toMillis,TimeUnit.MILLISECONDS)
  private var system:ActorSystem = _
  private var beeCacheRegion:ActorRef = _
  private val numberOfShards = config.getInt("server.number-of-shards")
  private def idExtractor:ShardRegion.ExtractEntityId = {
    case msg:EntityMessage =>(msg.key,msg)
  }
  private def shardIdExtractor:ShardRegion.ExtractShardId = {
    case msg:EntityMessage => (msg.key.hashCode % 100).toString
  }
  /**
    * 链接server
    */
  override def initialize(): Unit = {
    if(system == null)
      system = ActorSystem(clusterName, config)
    beeCacheRegion = ClusterSharding(system).startProxy(
      typeName = Constants.ENTITY_TYPE_NAME
      ,role = Optional.of("server")
      ,messageExtractor = BeeCacheMessageExtractor(numberOfShards))

  }

  /**
    * 断开server链接
    */
  override def destroy(): Unit = {
    val cluster = Cluster(system)
    cluster.leave(cluster.selfAddress)
    system.stop(beeCacheRegion)
    system.terminate()
  }

  override def get(key: String): Try[BeeCacheData] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Get(key),defaultTimeout.duration).asInstanceOf[BeeCacheData]
  }


  override def set(data: BeeCacheData): Try[Boolean] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Set(data.key,data.value,data.expireTime),defaultTimeout.duration) match {
      case _:EntityEvent.Updated => true
      case exception:BeeCacheException => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for set key[$data]: $otherResult")
    }
  }


  override def setExpire(key: String, expireTime: Long): Try[Boolean] = Try{
    Await.result(beeCacheRegion ? EntityCommand.SetExpire(key,expireTime),defaultTimeout.duration) match {
      case _:EntityEvent.Updated => true
      case exception:BeeCacheException => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for set key expire [$key]: $otherResult")
    }
  }


  override def delete(key: String): Try[Boolean] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Delete(key),defaultTimeout.duration) match {
      case _:EntityEvent.Deleted => true
      case exception:BeeCacheException => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for delete key[$key]: $otherResult")
    }
  }

  override def select(key: String): Try[BeeCacheData] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Select(key),defaultTimeout.duration) match {
      case data:BeeCacheData => data
      case exception:BeeCacheException => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for select key[$key]: $otherResult")
    }
  }

}
