package io.github.kpacha.heimdall.client

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorSystem
import spray.http._
import akka.io.IO
import spray.can.Http

trait ClientReq {
  private implicit val timeout: Timeout = 2.seconds

  def analyze(req: HttpRequest)(implicit system: ActorSystem): RequestAnalysis = new RequestAnalysis(req)

  def request(
    analysis: RequestAnalysis, host: String, port: Int = 80)(
    implicit system: ActorSystem): Future[HttpResponse] = {
      import system.dispatcher // ExecutionContext for the futures and scheduler
      for {
        Http.HostConnectorInfo(hostConnector, _) <- IO(Http) ? Http.HostConnectorSetup(host, port = port)
        response <- hostConnector.ask(analysis.request).mapTo[HttpResponse]
      } yield response
    }
}