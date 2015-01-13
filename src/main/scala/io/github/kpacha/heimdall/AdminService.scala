package io.github.kpacha.heimdall

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.can.Http
import spray.can.server.Stats
import spray.http._
import spray.routing._
import spray.routing.directives.CachingDirectives._
import spray.util._
import MediaTypes._

class AdminService extends HttpServiceActor {
  implicit val timeout: Timeout = 1.second // for the actor 'asks'
  import context.dispatcher // ExecutionContext for the futures and scheduler

  val simpleCache = routeCache(maxCapacity = 1000, timeToIdle = Duration("30 min"))

  def askStats(id: Int): Future[Stats] =
    (actorRefFactory.actorFor("/user/IO-HTTP/listener-" + id) ? Http.GetStats).mapTo[Stats]

  def futureStats(): Future[List[Stats]] = Future.sequence(List(askStats(0), askStats(1)))

  def receive = runRoute(myRoute)

  val myRoute =
    path("server-stats") {
      get {
        onSuccess(futureStats) { l: List[Stats] =>
          complete {
            statsPresentation(l.head, l.tail.head)
          }
        }
      }
    } ~
    path("") {
      cache(simpleCache) {
        complete {
          index
        }
      }
    }
  
  private lazy val index = HttpResponse(
    entity = HttpEntity(`text/html`,
    <html lang="en">
      <head>
          <title>Heimdall: Admin</title>
        <link href="//maxcdn.bootstrapcdn.com/bootswatch/3.3.0/cosmo/bootstrap.min.css" rel="stylesheet"/>
      </head>
      <body>
        <div class="jumbotron">
            <div class="container">
                <h1>Say hello to <i>heimdall</i>!</h1>
                <p>The reactive edge service based on actors.</p>
            </div>
        </div>
        <div class="container-fluid">
            <div class="row">
                <div class="col-md-offset-1 col-md-10">
                    <h3>Usage</h3>
                    <p>
                        Get the lookup data in json format:
                        More info on the <a href="https://github.com/kpacha/spray-geoip" target="_blank">project home page</a>
                    </p>

                    <h3>Defined resources:</h3>
                    <ul>
                      <li><a href="/server-stats">/server-stats</a></li>
                    </ul>
                    <h3>Prowered by</h3>
                    <ul>
                        <li>spray</li>
                    </ul>
                </div>
            </div>
        </div>
      </body>
    </html>.toString)
  )

  private def statsPresentation(s: Stats, r: Stats) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html lang="en">
        <head>
          <title>Heimdall: Http Stats</title>
          <link href="//maxcdn.bootstrapcdn.com/bootswatch/3.3.0/cosmo/bootstrap.min.css" rel="stylesheet"/>
        </head>
        <body>
          <div class="jumbotron">
              <div class="container">
                <h1>Say hello to <i>heimdall</i>!</h1>
                <p>The reactive edge service based on actors.</p>
              </div>
          </div>
          <div class="container-fluid">
              <div class="row">
                  <div class="col-md-offset-1 col-md-10">
                      <h3>HttpServer Stats</h3>
                        <table class="table table-striped">
                          <tr><th>stats</th><th>http-listener-0</th><th>http-listener-1</th></tr>
                          <tr><td>uptime:</td><td>{s.uptime.formatHMS}</td><td>{r.uptime.formatHMS}</td></tr>
                          <tr><td>totalRequests:</td><td>{s.totalRequests}</td><td>{r.totalRequests}</td></tr>
                          <tr><td>openRequests:</td><td>{s.openRequests}</td><td>{r.openRequests}</td></tr>
                          <tr><td>maxOpenRequests:</td><td>{s.maxOpenRequests}</td><td>{r.maxOpenRequests}</td></tr>
                          <tr><td>totalConnections:</td><td>{s.totalConnections}</td><td>{r.totalConnections}</td></tr>
                          <tr><td>openConnections:</td><td>{s.openConnections}</td><td>{r.openConnections}</td></tr>
                          <tr><td>maxOpenConnections:</td><td>{s.maxOpenConnections}</td><td>{r.maxOpenConnections}</td></tr>
                          <tr><td>requestTimeouts:</td><td>{s.requestTimeouts}</td><td>{r.requestTimeouts}</td></tr>
                        </table>
                  </div>
              </div>
          </div>
        </body>
      </html>.toString
    )
  )
}