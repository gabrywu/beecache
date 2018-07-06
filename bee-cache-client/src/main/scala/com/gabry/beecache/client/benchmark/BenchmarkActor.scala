package com.gabry.beecache.client.benchmark

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.gabry.beecache.protocol.BeeCacheData
import com.gabry.beecache.protocol.command.EntityCommand
import com.typesafe.config.Config

/**
  * Created by gabry on 2018/7/5 17:02
  */
class BenchmarkActor(regionProxy:ActorRef,config:Config,parallel:Int,messageNumber:Int) extends Actor with ActorLogging{
  var counter = 0
  var start:Long = 0L
  override def preStart(): Unit = {
    super.preStart()
    self ! "begin"

  }
  override def receive: Receive = {
    case "begin" =>
      val backendActor = 1 to parallel map{ i=>
        context.system.actorOf(Props(new BackendActor(self,regionProxy,messageNumber)),s"backend$i")
      }
      backendActor.foreach(_ ! "start")
      start = System.currentTimeMillis()
    case "end" =>
      counter += 1
      println(s"${sender()} done $counter")
      if(counter == parallel){
        val end = System.currentTimeMillis()
        log.info(s"parallel $parallel,message number per actor :$messageNumber,耗时${end-start}毫秒,平均速率${parallel*messageNumber*1000/(end-start)} ")
        context.stop(self)
      }
  }
}
class BackendActor(from:ActorRef,regionProxy:ActorRef,messageNumber:Int) extends Actor with ActorLogging{
  var counter  = 0
  var start = 0L
  override def receive: Receive = {
    case "start"=>
      1 to messageNumber foreach{ i =>
        regionProxy ! EntityCommand.Get("123")
      }
      start = System.currentTimeMillis()
    case _:BeeCacheData =>
      counter += 1
      if(counter == messageNumber){
        from ! "end"
        val end = System.currentTimeMillis()
        log.info(s"${self.path.name} message number $messageNumber,耗时${end-start}毫秒,平均速率${messageNumber*1000/(end-start)} ")
      //  context.stop(self)
      }
  }
}
