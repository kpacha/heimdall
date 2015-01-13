package io.github.kpacha.heimdall

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.{Config, ConfigValue}
import scala.collection.JavaConverters._ 
import spray.http.Uri
import java.util.Map.Entry

trait ConfigParser {
  val config: Config

  def getConfigInt(key: String): Int = config.getInt("heimdall." + key)
  def getConfigString(key: String): String = config.getString("heimdall." + key)
  def getConfigStringList(key: String): List[String] = config.getStringList("heimdall." + key).asScala.toList
  def getConfigObjectProperties(key: String): List[String] = config.getObject("heimdall." + key).asScala.keySet.toList
}
 
class SettingsImpl(val config: Config) extends Extension with ConfigParser {
  val adminHost: String = getConfigString("admin.host")
  val adminPort: Int = getConfigInt("admin.port")
  val proxyHost: String = getConfigString("proxy.host")
  val proxyPort: Int = getConfigInt("proxy.port")

  lazy val filters: List[String] = getConfigStringList("lifecycle")

  private def getMapping(key: String): String = getConfigString("mapping." + key)

  private def makeUri(host: String): Uri = (host split ":").toList match {
    case List(h, p) => Uri(h).withPort(p.toInt)
    case List(h) => Uri(h)
    case _ => throw new Exception(s"Bad host $host")
  }

  private def makeUris(key: String): List[Uri] =
    getConfigStringList("mapping." + key + ".source") map makeUri

  lazy val mapping: Map[(String, String), List[Uri]] = {
  	(getConfigObjectProperties("mapping").sorted(Ordering[String].reverse) map {
  		key: String => {
  			((getMapping(key + ".prefix"), getMapping(key + ".version")), makeUris(key))
  		}
  	}).toMap
  }
}

object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {
 
  override def lookup = Settings
 
  override def createExtension(system: ExtendedActorSystem) =
    new SettingsImpl(system.settings.config)
 
  /**
   * Java API: retrieve the Settings extension for the given system.
   */
  override def get(system: ActorSystem): SettingsImpl = super.get(system)
}