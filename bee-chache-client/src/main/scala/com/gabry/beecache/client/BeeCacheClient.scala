package com.gabry.beecache.client

import java.util.Optional
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding
import akka.pattern._
import akka.util.Timeout
import com.gabry.beecache.core.extractor.BeeCacheMessageExtractor
import com.gabry.beecache.protocol.BeeCacheData
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
  /**
    * 链接server
    */
  override def initialize(): Unit = {
    if(system == null)
      system = ActorSystem(clusterName, config)
    beeCacheRegion = ClusterSharding(system).startProxy(
      typeName = Constants.ENTITY_TYPE_NAME
      ,role = Optional.of("proxy")
      ,messageExtractor = BeeCacheMessageExtractor(numberOfShards))

  }

  /**
    * 断开server链接
    */
  override def destroy(): Unit = {
    system.stop(beeCacheRegion)
    system.terminate()
  }

  override def get(key: String): Try[BeeCacheData] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Get(key),defaultTimeout.duration) match {
      case Success(data:BeeCacheData) => data
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for get key[$key]: $otherResult")
    }
  }


  override def set(data: BeeCacheData): Try[Boolean] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Set(data.key,data.value,data.expireTime),defaultTimeout.duration) match {
      case Success(_:EntityEvent.Updated) => true
      case Success(exception:BeeCacheException) => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for set key[$data]: $otherResult")
    }
  }


  override def setExpire(key: String, expireTime: Long): Try[Boolean] = Try{
    Await.result(beeCacheRegion ? EntityCommand.SetExpire(key,expireTime),defaultTimeout.duration) match {
      case Success(_:EntityEvent.Updated) => true
      case Success(exception:BeeCacheException) => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for set key expire [$key]: $otherResult")
    }
  }


  override def delete(key: String): Try[Boolean] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Delete(key),defaultTimeout.duration) match {
      case Success(_:EntityEvent.Deleted) => true
      case Success(exception:BeeCacheException) => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for delete key[$key]: $otherResult")
    }
  }

  override def select(key: String): Try[BeeCacheData] = Try{
    Await.result(beeCacheRegion ? EntityCommand.Select(key),defaultTimeout.duration) match {
      case Success(data:BeeCacheData) => data
      case Success(exception:BeeCacheException) => throw exception
      case otherResult => throw UnknownBeeCacheException(s"Get unknown result for select key[$key]: $otherResult")
    }
  }

}
