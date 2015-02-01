package io.github.kpacha.heimdall

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import java.util.UUID
import spray.http.{HttpRequest, HttpResponse, Uri}

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

  lazy val sourceUris: List[Uri] = urlMapping match {
    case Some(UrlMapping(sources, _, _)) => sources
    case None => Nil
  }
  lazy val shadowUris: List[Uri] = urlMapping match {
    case Some(UrlMapping(_, sources, _)) => sources
    case None => Nil
  }
  lazy val filters: List[String] = urlMapping match {
    case Some(UrlMapping(_, _, f)) => f
    case None => Nil
  }

  lazy val headers = req.headers filter (_ isNot "host")
  lazy val request = HttpRequest(req.method, requestPath, headers, req.entity, req.protocol)

  private def normalize(path: String): String =
    if (path.startsWith("/")) path
    else "/" + path

  override def toString(): String = id + ": " + request + " [ " + req + "]"
}

abstract class HeimdallRequestContext() {
	val analysis: RequestAnalysis
	val originalSender: ActorRef
	val offset: Int = 0
	val request: HttpRequest
	val response: HttpResponse

	override def toString = analysis + " from " + originalSender
}

case class PreProcessedRequest(
  override val analysis: RequestAnalysis,
  override val originalSender: ActorRef,
  override val offset: Int = 0,
  override val request: HttpRequest, heimdallRequest: HttpRequest,
  override val response: HttpResponse = null
  ) extends HeimdallRequestContext

case class PostProcessedRequest(
	override val analysis: RequestAnalysis,
	override val originalSender: ActorRef,
	override val offset: Int = 0,
	override val request: HttpRequest, heimdallRequest: HttpRequest,
	override val response: HttpResponse, heimdallResponse: HttpResponse
	) extends HeimdallRequestContext

case class ErroredRequest(
  override val analysis: RequestAnalysis,
  override val originalSender: ActorRef,
  override val offset: Int = 0,
  override val request: HttpRequest,
  override val response: HttpResponse = null
  ) extends HeimdallRequestContext