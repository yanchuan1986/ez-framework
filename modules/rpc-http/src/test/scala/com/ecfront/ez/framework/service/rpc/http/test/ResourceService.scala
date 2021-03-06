package com.ecfront.ez.framework.service.rpc.http.test

import java.io.File

import com.ecfront.common.Resp
import com.ecfront.ez.framework.service.rpc.foundation._
import com.ecfront.ez.framework.service.rpc.http.HTTP
import com.ecfront.ez.framework.service.rpc.http.scaffold.SimpleHTTPService
import com.ecfront.ez.framework.service.storage.foundation.BaseStorage
import org.joox.JOOX._
import org.w3c.dom.Document

@RPC("/resource/")
@HTTP
object ResourceService extends SimpleHTTPService[EZ_Resource, EZRPCContext] {

  override protected val storageObj: BaseStorage[EZ_Resource] = EZ_Resource

  @POST("test/:s-s/:Sd")
  def test(parameter: Map[String, String], body: List[EZ_Resource], context: EZRPCContext): Resp[String] = {
    // 泛型，error
    Resp.success("")
  }

  @GET("xml/")
  def testgetXml(parameter: Map[String, String], context: EZRPCContext): Resp[Document] = {
    Resp.success($(
      s"""
         |<?xml version="1.0"?>
         |<china dn="day">
         |<city quName="黑龙江" pyName="heilongjiang" cityname="哈尔滨" state1="0" state2="0" stateDetailed="晴" tem1="7" tem2="-4" windState="北风3-4级转小于3级"/>
         |</china>
       """.stripMargin
    ).document())
  }

  @GET("xml/str")
  def testgetXmlStr(parameter: Map[String, String], context: EZRPCContext): Resp[String] = {
    Resp.success(
      s"""
         |<?xml version="1.0"?>
         |<china dn="day">
         |<city quName="黑龙江" pyName="heilongjiang" cityname="哈尔滨" state1="0" state2="0" stateDetailed="晴" tem1="7" tem2="-4" windState="北风3-4级转小于3级"/>
         |</china>
       """.stripMargin)
  }

  @POST("xml/")
  def testPostXml(parameter: Map[String, String], body: Document, context: EZRPCContext): Resp[Document] = {
    Resp.success(body)
  }

  @POST("xml/str/")
  def testPostXmlStr(parameter: Map[String, String], body: String, context: EZRPCContext): Resp[String] = {
    Resp.success(body)
  }

  @POST("xml/str/error/")
  def testPostXmlStrError(parameter: Map[String, String], body: String, context: EZRPCContext): Resp[String] = {
    Resp.badRequest("some error")
  }

  @POST("file/")
  def uploadFile(parameter: Map[String, String], body: String, context: EZRPCContext): Resp[String] = {
    Resp.success(body)
  }

  @GET("longtime/")
  def longTime(parameter: Map[String, String], context: EZRPCContext): Resp[String] = {
    Thread.sleep(10000000)
    Resp.success("ok")
  }

  @GET("downfile/")
  def downFile(parameter: Map[String, String], context: EZRPCContext): Resp[DownloadFile] = {
    val file=new File(this.getClass.getResource("/").getPath+"logback.xml")
    Resp.success(DownloadFile(file,"logback.xml"))
  }

}