package io.github.kpacha.heimdall.filter

import akka.util.Timeout
import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.http.{HttpMessage, HttpResponse}
import io.github.kpacha.heimdall.{PostProcessedRequest, UrlMapping}
import io.github.kpacha.heimdall.client.{ClientReq, RequestAnalysis}
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
    case PostProcessedRequest(uuid, originalsender, filters, or, req, resp, res) => {
      log.info("Continue the workflow {} -> [{}]", uuid, filters)
      val ctx = PostProcessedRequest(uuid, originalsender, filters.tail, or, req, resp, res)
      context.actorSelection(filters.head) ! ctx
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
    case PostProcessedRequest(_, _, _, _, req, _, res) => {
      val analysis = analyze(req)
      analysis.urlMapping match {
        case Some(UrlMapping(_, shadowUris, _)) => {
          val responseFutures = shadowUris map (uri => request(analysis, uri.toString, uri.authority.port))
          Future.fold(responseFutures)(List(res))(aggregate(analysis.rpprefix)) map storeWith(analysis)
        }
        case None => log.warning("A request to {} has arrived to the ShadowProxyFilter!!!", analysis.rpprefix)
      }
    }
  }
}