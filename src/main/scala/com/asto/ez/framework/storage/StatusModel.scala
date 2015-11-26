package com.asto.ez.framework.storage

import com.asto.ez.framework.storage.jdbc.Index

import scala.beans.BeanProperty

trait StatusModel extends BaseModel {

  @Index
  @BeanProperty var enable: Boolean = _

}

object StatusModel {
  val ENABLE_FLAG = "enable"
}







