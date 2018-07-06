package com.gabry.beecache.core.actor

import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, Cancellable, Props}
import akka.persistence.{RecoveryCompleted, _}
import com.gabry.beecache.protocol.BeeCacheData
import com.gabry.beecache.protocol.command.{Command, EntityCommand}
import com.gabry.beecache.protocol.constant.Constants
import com.gabry.beecache.protocol.event.EntityEvent
import com.gabry.beecache.protocol.exception.EntityException
import com.typesafe.config.Config

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
  * Created by gabry on 2018/6/27 15:08
  */
object BeeCacheActor{

  def props:Props = Props.create(classOf[BeeCacheActor],Constants.ENTITY_TYPE_NAME)

  private[BeeCacheActor] case object PassivateStop extends Command
  private[BeeCacheActor] case class ExpireTimeReached(expireTime:Long) extends Command

  private[BeeCacheActor] val defaultMaxTimeoutInMillis = 3*60*1000
}

class BeeCacheActor(entityTypeName:String) extends PersistentActor with ActorLogging{
  import akka.cluster.sharding.ShardRegion.Passivate
  val config: Config = context.system.settings.config
  val defaultTimeoutInMillis: Long = config.getDuration("server.entity-default-timeout").toMillis.max(BeeCacheActor.defaultMaxTimeoutInMillis)
  val snapshotMaxMessage:Int = config.getInt("server.snapshot-max-message")
  val defaultEntityData = BeeCacheData(self.path.name,None,defaultTimeoutInMillis)
  var entityData:BeeCacheData = defaultEntityData
  var cancelableTimeout:Cancellable = Cancellable.alreadyCancelled
  override def persistenceId: String = s"$entityTypeName-entity[${self.path.name}]"
  implicit val scheduleExecutionContext: ExecutionContextExecutor = context.system.dispatcher

  def createCancelableTimeout(expireTime:Long):Cancellable = {
    context.system.scheduler.scheduleOnce(Duration(expireTime,TimeUnit.MILLISECONDS)){
      self ! BeeCacheActor.ExpireTimeReached(expireTime)
    }
  }
  override def preStart(): Unit = {
    super.preStart()
    cancelableTimeout = createCancelableTimeout(entityData.expireTime)
  }

  override def postStop(): Unit = {
    super.postStop()
    cancelableTimeout.cancel()
  }

  /**
    * actor用persist函数将command序列化保存后，再使用该command对状态进行更新
    * @param updateCommand 更新状态的命令
    */
  def updateState(updateCommand:EntityCommand):Unit = {
    updateCommand match {
        case cmd: EntityCommand.Set =>
          entityData = entityData.copy(value = Some(cmd.value), expireTime = cmd.expireTime,versionNo = lastSequenceNr)
          cancelableTimeout.cancel()
          cancelableTimeout = createCancelableTimeout(entityData.expireTime)
        case cmd: EntityCommand.SetExpire =>
          entityData = entityData.copy(expireTime = cmd.expireTime,versionNo = lastSequenceNr)
          cancelableTimeout.cancel()
          cancelableTimeout = createCancelableTimeout(entityData.expireTime)
        case _: EntityCommand.Delete =>
          entityData = defaultEntityData
          cancelableTimeout.cancel()
        case unknownCommand =>
          log.error(s"updateState receive unknownCommand: $unknownCommand")
      }
    if( lastSequenceNr % snapshotMaxMessage ==0 && lastSequenceNr != 0 ){
      saveSnapshot(entityData)
    }
  }
  /**
    * actor被临时卸载或者超时卸载，需要可以恢复数据
    * actor恢复时，从存储读取数据反序列化成对应的command事件，传给该函数进行状态的回放更新
    */
  override def receiveRecover: Receive = {
    case cmd: EntityCommand.Set =>
      updateState(cmd)
    case cmd: EntityCommand.SetExpire =>
      updateState(cmd)
    case cmd: EntityCommand.Delete =>
      updateState(cmd)
    case SnapshotOffer(metadata,snapshot) if snapshot.isInstanceOf[BeeCacheData] =>
      log.info(s"SnapshotOffer metadata: $metadata")
      entityData = snapshot.asInstanceOf[BeeCacheData]
    case RecoveryCompleted =>
      cancelableTimeout = createCancelableTimeout(entityData.expireTime)
    case otherCmd =>
      log.warning(s"receiveRecover receive other command: $otherCmd")
  }

  def receiveEntityMessage:Receive = {
    case cmd: EntityCommand.Set =>
      val from = sender()
      persist(cmd)(updateState)
      from ! EntityEvent.Updated(cmd.key)
    case cmd: EntityCommand.SetExpire =>
      val from = sender()
      persist(cmd)(updateState)
      from ! EntityEvent.Updated(cmd.key)
    case cmd @ EntityCommand.Delete(key) =>
      val from = sender()
      persist(cmd)(updateState)
      self ! BeeCacheActor.ExpireTimeReached(entityData.expireTime)
      from ! EntityEvent.Deleted(key)
    case _:EntityCommand.Get =>
      sender() ! entityData
    case _:EntityCommand.Select if entityData.value.nonEmpty =>
      sender() ! entityData
    case EntityCommand.Select(key) if entityData.value.isEmpty =>
      sender() ! EntityException.KeyNotFound(key,s"Key[$key] not found ,you can set it first")
  }
  def receiveControlCommand:Receive = {
    case DeleteMessagesSuccess(toSequenceNr) =>
      log.info(s"Message up to $toSequenceNr deleted")
      context.parent ! Passivate(BeeCacheActor.PassivateStop)
    case DeleteMessagesFailure(cause,toSequenceNr) =>
      log.error(cause,s"Message up to $toSequenceNr delete failed")
    case DeleteSnapshotSuccess(metadata) =>
      log.info(s"Snapshot up to ${metadata.sequenceNr} deleted")
    case DeleteSnapshotFailure(metadata,cause) =>
      log.error(cause,s"Snapshot delete failed: $metadata")
    case BeeCacheActor.ExpireTimeReached(_) =>
      // 数据超时，应该是清空数据退出，此处逻辑需要仔细斟酌
      deleteMessages(lastSequenceNr)
      deleteSnapshot(snapshotSequenceNr)
    case BeeCacheActor.PassivateStop =>
      log.warning(s"entity $entityData destroying")
      context.stop(self)
  }
  def receiveUnknownMessage:Receive = {
    case unknownMessage =>
      log.error(s"Receive unknownMessage: $unknownMessage")
  }
  override def receiveCommand: Receive = receiveControlCommand orElse receiveEntityMessage orElse receiveUnknownMessage
}
