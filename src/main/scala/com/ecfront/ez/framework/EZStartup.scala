package com.ecfront.ez.framework

import com.ecfront.common.{Req, Resp}
import com.ecfront.ez.framework.module.auth.AuthService
import com.ecfront.ez.framework.module.auth.manage.Initiator
import com.ecfront.ez.framework.module.core.CommonUtils
import com.ecfront.ez.framework.module.schedule.ScheduleService
import com.ecfront.ez.framework.rpc.RPC.EChannel
import com.ecfront.ez.framework.rpc.{RPC, Server}
import com.ecfront.ez.framework.service.protocols.{JDBCService, RedisService}
import com.typesafe.scalalogging.slf4j.LazyLogging

trait EZStartup extends App with LazyLogging {

  protected def moduleName: String

  protected def preStartup() = {}

  protected def postStartup() = {}

  protected def customShutdownHook() = {}

  protected def customPublicExecuteInterceptor(method: String, uri: String, parameters: Map[String, String], interceptorInfo: Map[String, String]): Resp[_] = null

  protected def customInnerExecuteInterceptor(method: String, uri: String, parameters: Map[String, String], interceptorInfo: Map[String, String]): Resp[_] = null

  private def startup(): Unit = {
    preStartup()
    RedisService.init()
    JDBCService.init()
    if (ConfigContainer.serversConfig.publicServer != null) {
      publicServer = RPC.server
        .setChannel(EChannel.HTTP)
        .setHost(ConfigContainer.serversConfig.publicServer.host)
        .setPort(ConfigContainer.serversConfig.publicServer.port)
        .setRootUploadPath(ConfigContainer.serversConfig.publicServer.resourcePath)
        .setPreExecuteInterceptor({
          (method, uri, parameters, interceptorInfo) =>
            if (!uri.startsWith(ConfigContainer.serversConfig.publicServer.publicUriPrefix)) {
              val customResult = customPublicExecuteInterceptor(method, uri, parameters, interceptorInfo)
              if (customResult == null) {
                //未使用自定义方法
                if (parameters.contains(CommonUtils.TOKEN)) {
                  AuthService.authorizationPublicServer(method, uri, parameters(CommonUtils.TOKEN))
                } else {
                  Resp.badRequest(s"Missing required field : [ ${CommonUtils.TOKEN} ].")
                }
              } else {
                customResult
              }
            } else {
              Resp.success(Req.anonymousReq)
            }
        }).startup().autoBuilding(ConfigContainer.serversConfig.publicServer.servicePath)
      if (ConfigContainer.serversConfig.publicServer.authManage) {
        Initiator.init()
        publicServer.autoBuilding("com.ecfront.ez.framework.module.auth")
      }
      logger.info(s"Public Server  started at ${ConfigContainer.serversConfig.publicServer.host} : ${ConfigContainer.serversConfig.publicServer.port}")
    }
    if (ConfigContainer.serversConfig.clusterServer != null) {
      innerServer = RPC.server
        .setChannel(EChannel.EVENT_BUS)
        .setHost(ConfigContainer.serversConfig.clusterServer.host)
        .setPreExecuteInterceptor({
          (method, uri, parameters, interceptorInfo) =>
            val customResult = customInnerExecuteInterceptor(method, uri, parameters, interceptorInfo)
            if (customResult == null) {
              //未使用自定义方法
              if (parameters.contains(CommonUtils.TOKEN)) {
                AuthService.authorizationInnerServer(method, uri, parameters(CommonUtils.TOKEN))
              } else {
                Resp.badRequest(s"Missing required field : [ ${CommonUtils.TOKEN} ].")
              }
            } else {
              customResult
            }
        })
        .startup().autoBuilding(ConfigContainer.serversConfig.clusterServer.servicePath)
      logger.info(s"Inner Server  started at ${ConfigContainer.serversConfig.clusterServer.host}")
    }
    ScheduleService.runByModuleName(moduleName)
    postStartup()
    logger.info("RPC Service started.")
  }

  private def shutdownHook(): Unit = {
    sys.addShutdownHook({
      ScheduleService.stop()
      RedisService.close()
      JDBCService.close()
      publicServer.shutdown()
      innerServer.shutdown()
      customShutdownHook()
      logger.info("RPC Service shutdown.")
    })
  }

  startup()
  shutdownHook()

  private var publicServer: Server = _
  private var innerServer: Server = _

}