package com.gabry.beecache.core.actor

import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, Cancellable, Props}
import akka.persistence.{RecoveryCompleted, _}
import com.gabry.beecache.core.constant.Constants
import com.gabry.beecache.protocol.BeeCacheData
import com.gabry.beecache.protocol.command.{Command, EntityCommand}
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
  def props(entityId:String):Props = {
    println(s"BeeCacheActor.props called entityId=$entityId")
    Props.create(classOf[BeeCacheActor],Constants.ENTITY_TYPE_NAME)
  }

  private[BeeCacheActor] case object PassivateStop extends Command
  private[BeeCacheActor] case class ExpireTimeReached(expireTime:Long) extends Command

  private[BeeCacheActor] val defaultMaxTimeoutInMillis = 3*60*1000
}

class BeeCacheActor(entityTypeName:String) extends PersistentActor with ActorLogging{
  import akka.cluster.sharding.ShardRegion.Passivate

  private val config: Config = context.system.settings.config
  // 数据默认超时时间
  private val defaultTimeoutInMillis: Long = config.getDuration("server.entity-default-timeout").toMillis.max(BeeCacheActor.defaultMaxTimeoutInMillis)
  // 生成快照的最大消息数量。其实对于缓存来说，不需要历史数据来构建当前状态
  private val snapshotMaxMessage:Int = config.getInt("server.snapshot-max-message").min(1000)
  // 当前actor默认缓存数据。因为向shardRegion查询时，即使没有缓存数据，该actor也会创建，所以需要一个默认值返回
  private val defaultEntityData = BeeCacheData(self.path.name,None,defaultTimeoutInMillis)
  // 此actor关联的缓存数据的persistenceId其实就是KEY
  override def persistenceId: String = s"$entityTypeName-entity[${self.path.name}]"
  // 此actor关联的缓存数据
  private var entityData:BeeCacheData = defaultEntityData

  private var cancelableTimeout:Cancellable = Cancellable.alreadyCancelled
  implicit val scheduleExecutionContext: ExecutionContextExecutor = context.system.dispatcher

  private def reCreateCancelableTimeout(expireTime:Long):Cancellable = {
    cancelableTimeout.cancel()
    context.system.scheduler.scheduleOnce(Duration(expireTime,TimeUnit.MILLISECONDS)){
      self ! BeeCacheActor.ExpireTimeReached(expireTime)
    }
  }
  override def preStart(): Unit = {
    super.preStart()
    cancelableTimeout = reCreateCancelableTimeout(entityData.expireTime)
  }

  override def postStop(): Unit = {
    super.postStop()
    cancelableTimeout.cancel()
  }

  /**
    * actor用persist函数将command序列化保存后，再使用该command对状态进行更新
    * @param updateCommand 更新状态的命令
    */
  def updateState(updateCommand:EntityCommand,recover:Boolean = false):Unit = {
    updateCommand match {
        case cmd: EntityCommand.Set =>
          entityData = BeeCacheData(cmd.key,Some(cmd.value),cmd.expireTime,lastSequenceNr)
          if(!recover){
            reCreateCancelableTimeout(entityData.expireTime)
          }
        case cmd: EntityCommand.SetExpire =>
          entityData = entityData.copy(expireTime = cmd.expireTime,version = lastSequenceNr)
          if(!recover){
            reCreateCancelableTimeout(entityData.expireTime)
          }
        case _: EntityCommand.Delete =>
          entityData = defaultEntityData
          if(!recover){
            reCreateCancelableTimeout(entityData.expireTime)
          }
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
      updateState(cmd,recover = true)
    case cmd: EntityCommand.SetExpire =>
      updateState(cmd,recover = true)
    case cmd: EntityCommand.Delete =>
      updateState(cmd,recover = true)
    case SnapshotOffer(metadata,snapshot) if snapshot.isInstanceOf[BeeCacheData] =>
      log.info(s"SnapshotOffer metadata: $metadata")
      entityData = snapshot.asInstanceOf[BeeCacheData]
    case RecoveryCompleted =>
      cancelableTimeout.cancel()
      cancelableTimeout = reCreateCancelableTimeout(entityData.expireTime)
  }

  /**
    * 收到与实体相关的消息
    */
  def receiveEntityMessage:Receive = {
    case cmd: EntityCommand.Set =>
      val from = sender()
      persist(cmd){ persistCmd =>
        updateState(persistCmd)
        from ! EntityEvent.Updated(persistCmd.key)
      }
    case cmd: EntityCommand.SetExpire =>
      val from = sender()
      persist(cmd){ persistCmd =>
        updateState(persistCmd)
        from ! EntityEvent.Updated(persistCmd.key)
      }
    case cmd @ EntityCommand.Delete(key) =>
      val from = sender()
      persist(cmd){ persistCmd =>
        updateState(persistCmd)
        self ! BeeCacheActor.ExpireTimeReached(entityData.expireTime)
        from ! EntityEvent.Deleted(key)
      }

    case _:EntityCommand.Get =>
      sender() ! entityData
    case _:EntityCommand.Select if entityData.value.nonEmpty =>
      sender() ! EntityEvent.Selected(entityData.key,Right(entityData))
    case EntityCommand.Select(key) if entityData.value.isEmpty =>
      sender() ! EntityEvent.Selected(entityData.key,Left( EntityException.KeyNotFound(key,s"Key[$key] not found ,you can set it first")))
  }
  def receiveControlCommand:Receive = {
    case DeleteMessagesSuccess(toSequenceNr) =>
      log.info(s"Message up to $toSequenceNr deleted")
      context.parent ! Passivate(BeeCacheActor.PassivateStop)
    case DeleteMessagesFailure(cause,toSequenceNr) =>
      log.error(cause,s"Message up to $toSequenceNr delete failed")
      context.parent ! Passivate(BeeCacheActor.PassivateStop)
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

  override def receiveCommand: Receive = receiveControlCommand orElse receiveEntityMessage
}
