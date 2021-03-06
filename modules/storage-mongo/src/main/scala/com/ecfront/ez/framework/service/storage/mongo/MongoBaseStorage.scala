package com.ecfront.ez.framework.service.storage.mongo

import com.ecfront.common.{BeanHelper, JsonHelper, Resp}
import com.ecfront.ez.framework.service.storage.foundation.{BaseModel, BaseStorage, EZStorageContext, Page}
import com.ecfront.ez.framework.service.storage.mongo.SortEnum.SortEnum
import io.vertx.core.json.{JsonArray, JsonObject}

/**
  * Mongo基础持久化实现
  *
  * @tparam M 实体类型
  */
trait MongoBaseStorage[M <: BaseModel] extends BaseStorage[M] {

  protected var _entityInfo =
    if (MongoEntityContainer.CONTAINER.contains(tableName)) {
      MongoEntityContainer.CONTAINER(tableName)
    } else {
      MongoEntityContainer.buildingEntityInfo(_modelClazz, tableName)
      MongoEntityContainer.CONTAINER(tableName)
    }

  override def customTableName(newName: String): Unit = {
    MongoEntityContainer.CONTAINER.remove(tableName)
    tableName = newName
    MongoEntityContainer.buildingEntityInfo(_modelClazz, tableName)
    _entityInfo = MongoEntityContainer.CONTAINER(tableName)
  }

  override def doSave(model: M, context: EZStorageContext): Resp[M] = {
    val requireResp = storageCheck(model, _entityInfo, isUpdate = false)
    if (requireResp) {
      val save = convertToJsonObject(_entityInfo, model)
      MongoExecutor.save(_entityInfo, tableName, save, _modelClazz)
    } else {
      requireResp
    }
  }

  override def doUpdate(model: M, context: EZStorageContext): Resp[M] = {
    val requireResp = storageCheck(model, _entityInfo, isUpdate = true)
    if (requireResp) {
      val update = convertToJsonObject(_entityInfo, model)
      MongoExecutor.update(_entityInfo, tableName, model.id, update, _modelClazz)
    } else {
      requireResp
    }
  }

  override def doSaveOrUpdate(model: M, context: EZStorageContext): Resp[M] = {
    val requireResp = storageCheck(model, _entityInfo, model.id != null && model.id.nonEmpty)
    if (requireResp) {
      val saveOrUpdate = convertToJsonObject(_entityInfo, model)
      MongoExecutor.saveOrUpdate(_entityInfo, tableName, model.id, saveOrUpdate, _modelClazz)
    } else {
      requireResp
    }
  }

  override def doUpdateByCond(newValues: String, condition: String, parameters: List[Any], context: EZStorageContext): Resp[Void] = {
    MongoProcessor.updateByCond(tableName, packageCondition(condition), packageCondition(newValues))
  }

  override def doGetById(id: Any, context: EZStorageContext): Resp[M] = {
    MongoProcessor.getById(tableName, id.asInstanceOf[String], _modelClazz)
  }

  override def doGetByCond(condition: String, parameters: List[Any], context: EZStorageContext): Resp[M] = {
    MongoProcessor.getByCond(tableName, packageCondition(condition), _modelClazz)
  }

  override def doDeleteById(id: Any, context: EZStorageContext): Resp[Void] = {
    MongoProcessor.deleteById(tableName, id.asInstanceOf[String])
  }

  override def doDeleteByCond(condition: String, parameters: List[Any], context: EZStorageContext): Resp[Void] = {
    MongoProcessor.deleteByCond(tableName, packageCondition(condition))
  }

  override def doCount(condition: String, parameters: List[Any], context: EZStorageContext): Resp[Long] = {
    MongoProcessor.count(tableName, packageCondition(condition))
  }

  override def doExistById(id: Any, context: EZStorageContext): Resp[Boolean] = {
    MongoProcessor.exist(tableName, new JsonObject().put("_id", id))
  }

  override def doExistByCond(condition: String, parameters: List[Any], context: EZStorageContext): Resp[Boolean] = {
    MongoProcessor.exist(tableName, packageCondition(condition))
  }

  override def doFind(condition: String, parameters: List[Any], context: EZStorageContext): Resp[List[M]] = {
    MongoProcessor.find(tableName, packageCondition(condition), null, 0, _modelClazz)
  }

  override def doPage(condition: String, parameters: List[Any], pageNumber: Long, pageSize: Int, context: EZStorageContext): Resp[Page[M]] = {
    MongoProcessor.page(tableName, packageCondition(condition), pageNumber, pageSize, null, _modelClazz)
  }

  /**
    * 附加条件查找
    *
    * @param condition 过滤条件
    * @param sort      排序
    * @param limit     获取记录数，默认为0，表示获取所有
    * @param context   上下文
    * @return 查找结果
    */
  def findWithOpt(condition: String = "{}", sort: Map[String, SortEnum], limit: Int = 0, context: EZStorageContext = EZStorageContext()): Resp[List[M]] = {
    MongoProcessor.find(tableName, packageCondition(condition), convertSort(sort), limit, _modelClazz)
  }

  /**
    * 附加条件分页
    *
    * @param condition  过滤条件
    * @param pageNumber 当前页，从1开始
    * @param pageSize   每页条数
    * @param sort       排序
    * @param context    上下文
    * @return 分页结果
    */
  def pageWithOpt(
                   condition: String = "{}", pageNumber: Long = 1, pageSize: Int = 10,
                   sort: Map[String, SortEnum] = Map(), context: EZStorageContext = null): Resp[Page[M]] = {
    MongoProcessor.page(tableName, packageCondition(condition), pageNumber, pageSize, convertSort(sort), _modelClazz)
  }

  /**
    * 聚合计算
    *
    * 例如：
    * [{ "$$match": {<过滤条件>} },
    * {
    * // Group
    * "$$group": {
    * "_id": {
    * "platform":"$$platform",
    * "component":"$$component",
    * "module":"$$module",
    * "stage":"$$stage"
    * },
    * "count": { "$$sum": 1 }
    * }
    * }]
    *
    * @param condition 计算条件
    * @param context   上下文
    * @return 计算结果
    */
  def aggregate(condition: JsonArray, context: EZStorageContext = null): Resp[JsonArray] = {
    MongoProcessor.aggregate(tableName, condition)
  }

  protected def convertToJsonObject(entityInfo: MongoEntityInfo, model: M): JsonObject = {
    packageCondition(JsonHelper.toJsonString(BeanHelper.findValues(model).filter(_._2 != null)))
  }

  private def packageCondition(condition: String): JsonObject = {
    if (condition == null || condition.trim == "") {
      new JsonObject("{}")
    } else {
      val cond=new JsonObject(condition)
      if(cond.containsKey("id")){
        cond.put("_id",cond.getValue("id"))
        cond.remove("id")
      }
      cond
    }
  }

  protected def convertSort(sort: Map[String, SortEnum]): JsonObject = {
    if (sort == null || sort.isEmpty) {
      null
    } else {
      val s = new JsonObject()
      sort.foreach(i => s.put(i._1, i._2.id))
      s
    }
  }

}

/**
  * 排序枚举
  */
object SortEnum extends Enumeration {
  type SortEnum = Value
  val DESC = Value(-1)
  val ASC = Value(1)
}


