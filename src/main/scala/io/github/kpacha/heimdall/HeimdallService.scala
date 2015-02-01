package io.github.kpacha.heimdall

import akka.actor.{Actor, ActorLogging}
import spray.can.Http
import spray.http.{HttpRequest, HttpResponse, Timedout}

class HeimdallService extends Actor with ActorLogging {
  import context.system

  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)
    case req: HttpRequest => {
      val analysis = new RequestAnalysis(req)
      if (analysis.filters.isEmpty)
        context.actorSelection("../../error-router") ! ErroredRequest(analysis, sender, 0, req)
      else {
        val ctx = PreProcessedRequest(analysis, sender, 0, req, req)
        log.info("Request: {}", ctx)
        context.actorSelection(analysis.filters.head) ! ctx
      }
    }
    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out...")
  }
}