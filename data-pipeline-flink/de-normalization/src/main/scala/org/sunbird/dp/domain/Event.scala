package org.sunbird.dp.domain

import java.util

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.collection.JavaConverters._
import scala.collection.mutable.Map

class Event(eventMap: util.Map[String, Any]) extends Events(eventMap) {

  private[this] val df = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZoneUTC
  private[this] val df2 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZoneUTC()
  private val jobName = "PipelinePreprocessor"

  override def kafkaKey(): String = {
    did()
  }

  def addDeviceProfile(deviceProfile: DeviceProfile): Unit = {

    val deviceMap = new util.HashMap[String, Object]()
    val userDeclaredMap = new util.HashMap[String, Object]()
    val iso3166statecode = addISOStateCodeToDeviceProfile(deviceProfile)
    val ldata = Map[String, AnyRef]("countrycode" -> deviceProfile.countryCode,
      "country" -> deviceProfile.country,
      "statecode" -> deviceProfile.stateCode,
      "state" -> deviceProfile.state,
      "city" -> deviceProfile.city,
      "statecustomcode" -> deviceProfile.stateCodeCustom,
      "statecustomname" -> deviceProfile.stateCustomName,
      "districtcustom" -> deviceProfile.districtCustom,
      "devicespec" -> deviceProfile.devicespec,
      "firstaccess" -> deviceProfile.firstaccess.asInstanceOf[AnyRef],
      "iso3166statecode" -> iso3166statecode)

    deviceMap.putAll(ldata.asJava)
    userDeclaredMap.putAll(Map[String, String]("state" -> deviceProfile.userDeclaredState, "district" -> deviceProfile.userDeclaredDistrict).asJava)
    deviceMap.put("userdeclared", userDeclaredMap)
    telemetry.add(path.deviceData(), deviceMap)
    setFlag("device_denorm", value = true)
  }

  def getUserProfileLocation(): Option[(String, String, String)] = {

    val userData: util.Map[String, AnyRef] = telemetry.read(path.userData()).getOrElse(null)
    if (null != userData)
      Some(userData.get("state").asInstanceOf[String], userData.get("district").asInstanceOf[String], "user-profile")
    else
      None
  }

  def getUserDeclaredLocation(): Option[(String, String, String)] = {

    val deviceData: util.Map[String, AnyRef] = telemetry.read(path.deviceData()).getOrElse(null)
    if (null != deviceData && null != deviceData.get("userdeclared")) {
      val userDeclared = deviceData.get("userdeclared").asInstanceOf[util.Map[String, String]]
      Some(userDeclared.get("state"), userDeclared.get("district"), "user-declared")
    } else
      None
  }

  def getIpLocation(): Option[(String, String, String)] = {
    val deviceData: util.Map[String, AnyRef] = telemetry.read(path.deviceData()).getOrElse(null)
    if (null != deviceData)
      Some(deviceData.get("state").asInstanceOf[String], deviceData.get("districtcustom").asInstanceOf[String], "ip-resolved")
    else
      None
  }

  def addDerivedLocation(derivedData: (String, String, String)) {
    val locMap = new util.HashMap[String, String]()
    locMap.put(path.stateKey(), derivedData._1)
    locMap.put(path.districtKey(), derivedData._2)
    locMap.put(path.locDerivedFromKey(), derivedData._3)
    telemetry.add(path.derivedLocationData(), locMap)
    setFlag("loc_denorm", true)
  }

  def compareAndAlterEts(): Long = {
    val eventEts = ets()
    val endTsOfCurrentDate = DateTime.now().plusDays(1).withTimeAtStartOfDay().minusMillis(1).getMillis
    if (eventEts > endTsOfCurrentDate) telemetry.add("ets", endTsOfCurrentDate)
    ets()
  }

  def isOlder(periodInMonths: Int): Boolean = {
    val eventEts = ets()
    val periodInMillis = new DateTime().minusMonths(periodInMonths).getMillis()
    eventEts < periodInMillis
  }

  def objectRollUpl1ID(): String = {
    telemetry.read[String]("object.rollup.l1").getOrElse(null)
  }

  def objectRollUpl1FieldsPresent(): Boolean = {

    val objectrollUpl1 = telemetry.read[String]("object.rollup.l1").getOrElse(null)
    null != objectrollUpl1 && !objectrollUpl1.isEmpty()
  }

  def checkObjectIdNotEqualsRollUpl1Id(): Boolean = {
    objectRollUpl1FieldsPresent() && !(objectID().equals(objectRollUpl1ID()))
  }

  def addUserData(newData: Map[String, AnyRef]) {
    val userdata: util.Map[String, AnyRef] = telemetry.read(path.userData()).getOrElse(new util.HashMap[String, AnyRef]())
    userdata.putAll(newData.asJava)
    telemetry.add(path.userData(), userdata)
    if (newData.size > 2)
      setFlag("user_denorm", true)
    else
      setFlag("user_denorm", false)
  }

  def addContentData(newData: Map[String, AnyRef]) {
    val convertedData = getEpochConvertedContentDataMap(newData)
    val contentData: util.Map[String, AnyRef] = telemetry.read(path.contentData()).getOrElse(new util.HashMap[String, AnyRef]())
    contentData.putAll(convertedData.asJava)
    telemetry.add(path.contentData(), contentData)
    setFlag("content_denorm", true)
  }

  def addCollectionData(newData: Map[String, AnyRef]) {
    val collectionMap = new util.HashMap[String, AnyRef]()
    val convertedData = getEpochConvertedContentDataMap(newData)
    collectionMap.putAll(convertedData.asJava)
    telemetry.add(path.collectionData(), collectionMap)
    setFlag("coll_denorm", true)
  }

  def getEpochConvertedContentDataMap(data: Map[String, AnyRef]): Map[String, AnyRef] = {

    val lastSubmittedOn = data.get("lastsubmittedon")
    val lastUpdatedOn = data.get("lastupdatedon")
    val lastPublishedOn = data.get("lastpublishedon")
    if (lastSubmittedOn.nonEmpty && lastSubmittedOn.get.isInstanceOf[String]) {
      data.put("lastsubmittedon", getConvertedTimestamp(lastSubmittedOn.get.asInstanceOf[String]).asInstanceOf[AnyRef])
    }
    if (lastUpdatedOn.nonEmpty && lastUpdatedOn.get.isInstanceOf[String]) {
      data.put("lastupdatedon", getConvertedTimestamp(lastUpdatedOn.get.asInstanceOf[String]).asInstanceOf[AnyRef])
    }
    if (lastPublishedOn.nonEmpty && lastPublishedOn.get.isInstanceOf[String]) {
      data.put("lastpublishedon", getConvertedTimestamp(lastPublishedOn.get.asInstanceOf[String]).asInstanceOf[AnyRef])
    }
    data
  }

  def addDialCodeData(newData: Map[String, AnyRef]) {
    val dialcodeMap = new util.HashMap[String, AnyRef]()
    dialcodeMap.putAll(getEpochConvertedDialcodeDataMap(newData).asJava)
    telemetry.add(path.dialCodeData(), dialcodeMap)
    setFlag("dialcode_denorm", true)
  }

  private def getEpochConvertedDialcodeDataMap(data: Map[String, AnyRef]): Map[String, AnyRef] = {

    val generatedOn = data.get("generatedon")
    val publishedOn = data.get("publishedon")
    if (generatedOn.nonEmpty && generatedOn.get.isInstanceOf[String]) {
      data.put("generatedon", getConvertedTimestamp(generatedOn.get.asInstanceOf[String]).asInstanceOf[AnyRef])
    }
    if (publishedOn.nonEmpty && publishedOn.get.isInstanceOf[String]) {
      data.put("publishedon", getConvertedTimestamp(publishedOn.get.asInstanceOf[String]).asInstanceOf[AnyRef])
    }
    data
  }

  def getTimestamp(ts: String, df: DateTimeFormatter): Long = {
    try {
      df.parseDateTime(ts).getMillis()
    } catch {
      case ex: Exception =>
        0L
    }
  }

  def getConvertedTimestamp(ts: String): Long = {
    val epochTs = getTimestamp(ts, df)
    if (epochTs == 0) {
      getTimestamp(ts, df2)
    } else {
      epochTs
    }
  }

  def setFlag(key: String, value: Boolean) {
    val telemetryFlag: util.Map[String, AnyRef] = telemetry.read(path.flags()).getOrElse(null)
    val flags = if (null == telemetryFlag) new util.HashMap[String, AnyRef]() else telemetryFlag
    flags.put(key, value.asInstanceOf[AnyRef])
    telemetry.add(path.flags(), flags)
  }

  def addISOStateCodeToDeviceProfile(deviceProfile: DeviceProfile): String = {
    // add new statecode field
    val statecode = deviceProfile.stateCode
    if (statecode != null && !statecode.isEmpty()) {
      "IN-" + statecode
    } else ""
  }

}
