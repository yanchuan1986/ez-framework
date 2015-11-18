package com.asto.ez.framework.service.scheduler

import com.ecfront.common.JsonHelper
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.quartz.impl.StdSchedulerFactory

import scala.concurrent.ExecutionContext.Implicits.global

object SchedulerService extends LazyLogging {

  private val quartzScheduler = StdSchedulerFactory.getDefaultScheduler

  def save(scheduler: EZ_Scheduler): Unit = {
    scheduler.parameterstr = JsonHelper.toJsonString(scheduler.parameters)
    scheduler.save().onSuccess {
      case saveResp =>
        if (saveResp) {
          JobHelper.add(scheduler.name, scheduler.cron, classOf[VertxJob], packageScheduler(scheduler), quartzScheduler)
        }
    }
  }

  def update(scheduler: EZ_Scheduler): Unit = {
    scheduler.parameterstr = JsonHelper.toJsonString(scheduler.parameters)
    scheduler.update().onSuccess {
      case updateResp =>
        if (updateResp) {
          JobHelper.modify(scheduler.name, scheduler.cron, classOf[VertxJob], packageScheduler(scheduler), quartzScheduler)
        }
    }
  }

  private def packageScheduler(scheduler: EZ_Scheduler): Map[String, Any] = {
    Map(
      "id" -> scheduler.id,
      "cron" -> scheduler.cron,
      "clazz" -> scheduler.clazz,
      "parameters" -> scheduler.parameterstr
    )
  }

  def delete(name: String): Unit = {
    EZ_Scheduler().deleteByCond(" name =? ", List(name)).onSuccess {
      case deleteResp =>
        if (deleteResp) {
          JobHelper.remove(name, quartzScheduler)
        }
    }
  }

  def init(): Unit = {
    logger.debug("Startup scheduling.")
    quartzScheduler.start()
    EZ_Scheduler().find().onSuccess {
      case findResp =>
        if (findResp) {
          findResp.body.foreach {
            job =>
              job.parameters = JsonHelper.toGenericObject[Map[String, Any]](job.parameterstr)
              JobHelper.add(job.name, job.cron, classOf[VertxJob], packageScheduler(job), quartzScheduler)
          }
        }
    }
  }

  def shutdown(): Unit = {
    quartzScheduler.shutdown()
  }

}