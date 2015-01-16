package io.github.kpacha.heimdall

import akka.actor.{Actor, ActorLogging}
import io.github.kpacha.heimdall.client.RequestAnalysis
import spray.can.Http
import spray.http.{HttpRequest, HttpResponse, Timedout}
import java.util.UUID

class HeimdallService extends Actor with ActorLogging {
  import context.system

  def analyze(req: HttpRequest): RequestAnalysis = new RequestAnalysis(req)
  def createUuid = UUID.randomUUID.toString

  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)
    case req: HttpRequest => {
      val filters: List[String] = analyze(req).urlMapping match {
        case Some(UrlMapping(_, _, f)) => f
        case None => Nil
      }
      val ctx = PreProcessedRequest(createUuid, sender, filters.tail, req, req)
      log.info("Request: {}", ctx)
      context.actorSelection(filters.head) ! ctx
    }
    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out...")
  }
}