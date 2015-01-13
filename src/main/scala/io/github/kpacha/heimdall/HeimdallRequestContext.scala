package io.github.kpacha.heimdall

import akka.actor.ActorRef
import spray.http.{HttpRequest, HttpResponse}

abstract class HeimdallRequestContext() {
	val id: String
	val originalSender: ActorRef
	val filters: List[String] = Nil
	val request: HttpRequest
	val response: HttpResponse

	override def toString = request + " -> " + response + " [" + filters + "]"
}

case class PreProcessedRequest(
	override val id: String,
	override val originalSender: ActorRef,
	override val filters: List[String],
	override val request: HttpRequest, heimdallRequest: HttpRequest,
	override val response: HttpResponse = null
	) extends HeimdallRequestContext

case class PostProcessedRequest(
	override val id: String,
	override val originalSender: ActorRef,
	override val filters: List[String],
	override val request: HttpRequest, heimdallRequest: HttpRequest,
	override val response: HttpResponse, heimdallResponse: HttpResponse
	) extends HeimdallRequestContext