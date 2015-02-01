package io.github.kpacha.heimdall.filter

import akka.actor.{Actor, ActorLogging}
import io.github.kpacha.heimdall.{ErroredRequest, UrlMapping}
import spray.http._
import MediaTypes._

class ErrorFilter extends Actor with ActorLogging {
  import context.system

  def receive = {
  	case ErroredRequest(analysis, originalsender, offset, _, _) => {
  	  log.warning("[{}] Aborting the workflow {} -> [{}] responding to {}",
  	  	offset, analysis.id, analysis.filters.drop(offset + 1), originalsender)
      originalsender ! index(analysis.mapping)
    }
  }
	
  private def index(mapping: Map[(String, String), UrlMapping]) = HttpResponse(
    status = 404,
    entity = HttpEntity(`text/html`, pattern.replaceAllIn(indexTemplate, replacement(mapping.keySet))))

  private lazy val pattern = """(<!-- placeholder -->)""".r

  private def aggregate(s: String, p: (String, String)): String =
    "<li><a href=\"/" + p._1 + "/" + p._2 + "\">/" + p._1 + "/" + p._2 + "</a></li>" + s
  
  private def replacement(mapping: Set[(String, String)]): String = mapping.foldLeft("")(aggregate)
  
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