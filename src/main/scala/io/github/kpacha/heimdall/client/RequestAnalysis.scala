package io.github.kpacha.heimdall.client

import akka.actor.{Actor, ActorLogging, ActorSystem}
import io.github.kpacha.heimdall.{Settings, UrlMapping}
import spray.http.{HttpRequest, Uri}
import java.util.UUID

class RequestAnalysis (req: HttpRequest)(
  implicit system: ActorSystem) {

  val settings = Settings(system)
  lazy val mapping = settings.mapping

  lazy val id = UUID.randomUUID().toString
  val relativePath: String = req.uri.toRelative.toString
  val prefix: Array[String] = relativePath.split('/').drop(1).take(2)
  lazy val rpprefix = "/" + prefix.mkString("/")
  lazy val requestPath = normalize(relativePath.drop(rpprefix.length))
  lazy val urlMapping: Option[UrlMapping] =
    if (prefix.length == 2) mapping.get((prefix(0), prefix(1)))
    else None

  lazy val headers = req.headers filter (_ isNot "host")
  lazy val request = HttpRequest(req.method, requestPath, headers, req.entity, req.protocol)

  private def normalize(path: String): String =
    if (path.startsWith("/")) path
    else "/" + path

  override def toString(): String = id + ": " + request + " [ " + req + "]"
}