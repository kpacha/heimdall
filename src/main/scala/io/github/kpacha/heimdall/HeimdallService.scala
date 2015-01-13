package io.github.kpacha.heimdall

import akka.actor.{Actor, ActorLogging}
import spray.can.Http
import spray.http.{HttpRequest, HttpResponse, Timedout}
import java.util.UUID

class HeimdallService extends Actor with ActorLogging {
  import context.system

  val settings = Settings(system)
  def createUuid() = UUID.randomUUID().toString

  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)
    case req: HttpRequest => {
      val ctx = PreProcessedRequest(createUuid, sender, settings.filters.tail, req, req)
      log.info("Request: {}", ctx)
      context.actorSelection(settings.filters.head) ! ctx
    }
    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out...")
  }
}