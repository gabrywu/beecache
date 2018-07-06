package com.gabry.beecache.client.actor

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.gabry.beecache.protocol.BeeCacheData
import com.gabry.beecache.protocol.command.EntityCommand

/**
  * Created by gabry on 2018/7/3 14:41
  */
class BeeCacheClientActor(regionProxy:ActorRef) extends Actor with ActorLogging{
  private val msgNumber = 1*10000
  private var counter = 0
  private var start:Long = 0
  private var end:Long = 0
  private var targetEntity:ActorRef = _
  override def receive: Receive = {
    case "start" =>
      if(targetEntity==null){
        regionProxy ! EntityCommand.Get("123")
      }

    case data:BeeCacheData =>
      if(targetEntity==null){
        targetEntity = sender()
        start = System.currentTimeMillis()
        0 until msgNumber foreach { i =>
          targetEntity ! EntityCommand.Get("123")
        }
      }
      counter += 1
      if(counter>=msgNumber){
        end = System.currentTimeMillis()
        println(s"${self.path.name} send $counter msg ${end-start} 毫秒")
      }

  }
}
