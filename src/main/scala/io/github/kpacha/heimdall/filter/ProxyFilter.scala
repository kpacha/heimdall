package io.github.kpacha.heimdall.filter

import scala.concurrent.duration._
import akka.util.Timeout
import spray.can.Http
import spray.util._
import spray.http._
import HttpHeaders.{`Content-Type`, Location}
import MediaTypes._
import scala.util.{Success, Failure}

import io.github.kpacha.heimdall.client.ClientReq
import io.github.kpacha.heimdall.proxy.DecoratedProxyActor
import io.github.kpacha.heimdall.{PreProcessedRequest, PostProcessedRequest}

class ProxyFilter extends ClientReq with DecoratedProxyActor {
  implicit val timeout: Timeout = 1.second // for the actor 'asks'
  import context.dispatcher // ExecutionContext for the futures and scheduler
  import context.system

  def receive = {
    case PreProcessedRequest(uuid, originalsender, filters, or, req, resp) => {
      val analysis = analyze(req)
      analysis.backendHosts match {
        case Some(uri :: _) => {
          log.info("[{}] -> {}", analysis, uri.authority)
          request(analysis, uri.toString(), uri.authority.port) onComplete {
            case Success(res) => {
              log.info("Response: {} - {}", res.status, res.entity match {
                case HttpEntity.NonEmpty(contentType, _) => contentType.toString
                case _ => ""
              })
  	          log.info("Continue the workflow {} -> [{}]", uuid, filters)
              context.actorSelection(filters.head) !
                PostProcessedRequest(uuid, originalsender, filters.tail, or, req, resp, processResponse(res, analysis.rpprefix))
            }
            case Failure(error) => {
              log.warning("Error [{}]: {}", uuid, error)
              originalsender ! index(analysis.mapping)
            }
          }
        }
        case _ => {
          log.warning("Error: Undefined origin for the prefix [{}]!", analysis.rpprefix)
          originalsender ! index(analysis.mapping)
        }
      }
    }
  }

  private def index(mapping: Map[(String, String), List[Uri]]) = HttpResponse(
    status = 404,
    entity = HttpEntity(`text/html`, pattern.replaceAllIn(indexTemplate, replacement(mapping))))

  private lazy val pattern = """(<!-- placeholder -->)""".r

  private def aggregate(s: String, p: (String, String)): String =
    "<li><a href=\"/" + p._1 + "/" + p._2 + "\">/" + p._1 + "/" + p._2 + "</a></li>" + s
  
  private def replacement(mapping: Map[(String, String), List[Uri]]): String = mapping.keySet.foldLeft("")(aggregate)
  
  private lazy val indexTemplate = 
      <html lang="en">
        <head>
          <title>Heimdall: Page Not Found</title>
          <link href="//maxcdn.bootstrapcdn.com/bootswatch/3.3.0/cosmo/bootstrap.min.css" rel="stylesheet"/>
        </head>
        <body>
          <div class="jumbotron">
              <div class="container">
                <h1>Page Not Found!</h1>
                <p>The server was unable to resolve the requested url.</p>
              </div>
          </div>
          <div class="container-fluid">
              <div class="row">
                  <div class="col-md-offset-1 col-md-10">
                      <h3>Defined resources:</h3>
                      <ul>
                        <!-- placeholder -->
                      </ul>
                  </div>
              </div>
          </div>
        </body>
      </html>.toString
}
