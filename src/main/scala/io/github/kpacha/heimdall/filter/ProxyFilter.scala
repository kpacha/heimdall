package io.github.kpacha.heimdall.filter

import scala.concurrent.duration._
import akka.util.Timeout
import spray.can.Http
import spray.util._
import spray.http._
import HttpHeaders.{`Content-Type`, Location}
import scala.util.{Success, Failure}

import io.github.kpacha.heimdall.client.ClientReq
import io.github.kpacha.heimdall.proxy.DecoratedProxyActor
import io.github.kpacha.heimdall.{ErroredRequest, PreProcessedRequest, PostProcessedRequest, UrlMapping}

class ProxyFilter extends ClientReq with DecoratedProxyActor {
  implicit val timeout: Timeout = 1.second // for the actor 'asks'
  import context.dispatcher // ExecutionContext for the futures and scheduler
  import context.system

  def receive = {
    case PreProcessedRequest(analysis, originalsender, offset, or, req, resp) => {
      if (analysis.sourceUris.isEmpty) {
        log.warning("Error: Undefined origin for the prefix [{}]!", analysis.rpprefix)
        context.actorSelection("../../error-router") ! ErroredRequest(analysis, sender, 0, or)
      } else {
        val uri: Uri = analysis.sourceUris.head
        request(analysis, uri.toString(), uri.authority.port) onComplete {
          case Success(res) => {
            val nextFilter = offset + 1
	          log.info("[{}] Continue the workflow {} -> [{}]", offset, analysis.id,
              analysis.filters.drop(nextFilter))
            context.actorSelection(analysis.filters(nextFilter)) !
              PostProcessedRequest(analysis, originalsender, nextFilter, or, req, resp, processResponse(res, analysis.rpprefix))
          }
          case Failure(error) => {
            log.warning("Error [{}]: {}", analysis.id, error)
            context.actorSelection("../../error-router") ! ErroredRequest(analysis, sender, 0, or)
          }
        }
      }
    }
  }
}
