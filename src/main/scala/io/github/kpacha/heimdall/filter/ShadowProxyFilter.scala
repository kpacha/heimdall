package io.github.kpacha.heimdall.filter

import akka.util.Timeout
import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.http.{HttpMessage, HttpResponse}
import io.github.kpacha.heimdall.{RequestAnalysis, PostProcessedRequest, UrlMapping}
import io.github.kpacha.heimdall.client.ClientReq
import io.github.kpacha.heimdall.proxy.DecoratedProxyActor

class ShadowProxyForkFilter extends Actor with ActorLogging {
  import context.system
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: Exception => Restart
  }

  var child: ActorRef = _

  override def preStart() {
    child = context.actorOf(Props[ShadowProxyFilter], "shadow-proxy")
  }

  def receive = {
    case PostProcessedRequest(analysis, originalsender, offset, or, req, resp, res) => {
      val nextFilter = offset + 1
      log.info("[{}] Continue the workflow {} -> [{}]", offset, analysis.id, analysis.filters.drop(nextFilter))
      val ctx = PostProcessedRequest(analysis, originalsender, nextFilter, or, req, resp, res)
      context.actorSelection(analysis.filters(nextFilter)) ! ctx
      child ! ctx
    }
  }
}

class ShadowProxyFilter extends ClientReq with DecoratedProxyActor {
  implicit val timeout: Timeout = 1.second
  import context.dispatcher
  import context.system

  def storeWith(analysis: RequestAnalysis)(responses: List[HttpMessage]): Unit = {
    store(analysis.id)(analysis)
    responses foreach store(analysis.id)
  }

  def aggregate(rpprefix: String)(rs: List[HttpResponse], r: HttpResponse): List[HttpResponse] =
    processResponse(r, rpprefix) :: rs

  private def store[T](id: String)(entity: T) = persist(List(id, entity))

  private def persist[T](entry: T) = log.debug("ShadowResponse: {}", entry.toString)

  def receive = {
    case PostProcessedRequest(analysis, _, _, _, req, _, res) => {
      if(analysis.shadowUris.isEmpty)
        log.warning("A request to {} has arrived to the ShadowProxyFilter!!!", analysis.rpprefix)
      else {
        val responseFutures = analysis.shadowUris map (uri =>
          request(analysis, uri.toString, uri.authority.port))
        Future.fold(responseFutures)(List(res))(aggregate(analysis.rpprefix)) map storeWith(analysis)
      }
    }
  }
}