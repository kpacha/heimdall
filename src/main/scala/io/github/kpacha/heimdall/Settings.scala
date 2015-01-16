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

case class UrlMapping(val mainPool: List[Uri], val shadowPool: List[Uri], val filters: List[String])

trait ConfigParser {
  val config: Config

  def getConfigInt(key: String): Int = config.getInt("heimdall." + key)
  def getConfigString(key: String): String = config.getString("heimdall." + key)
  def getConfigStringList(key: String, defaultValue: List[String] = Nil): List[String] =
    if (config.hasPath("heimdall." + key)) config.getStringList("heimdall." + key).asScala.toList
    else defaultValue

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

  private def makeUris(key: String): List[Uri] = getConfigStringList("mapping." + key) map makeUri

  private def mappigFilters(key: String): List[String] =
    getConfigStringList("mapping." + key + ".lifecycle", filters)

  private def urlMapping(key: String): ((String, String), UrlMapping) =
    ((getMapping(key + ".prefix"), getMapping(key + ".version")),
      UrlMapping(makeUris(key + ".source"), makeUris(key + ".shadow"), mappigFilters(key)))

  lazy val mapping: Map[(String, String), UrlMapping] =
    (getConfigObjectProperties("mapping").sorted(Ordering[String].reverse) map urlMapping).toMap
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