package io.github.kpacha.heimdall

import akka.actor.{Actor, ActorRef, Kill}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.can.Http
import spray.can.server.Stats
import spray.http._
import spray.routing._
import spray.routing.directives.CachingDirectives._
import spray.httpx.TwirlSupport
import spray.util._
import MediaTypes._

class AdminService extends HttpServiceActor with TwirlSupport {
  implicit val timeout: Timeout = 3.second // for the actor 'asks'
  import context.dispatcher // ExecutionContext for the futures and scheduler

  val simpleCache = routeCache(maxCapacity = 1000, timeToIdle = Duration("30 min"))

  val settings = Settings(context.system)
  val homeUrl = "http://" + settings.adminHost + ":" + settings.adminPort + "/"

  def getActor(id: String): ActorRef = actorRefFactory.actorFor(id)
  def getHttpListener(id: Int): ActorRef = getActor("/user/IO-HTTP/listener-" + id)

  def askStats(id: String): Future[Stats] = (getActor(id) ? Http.GetStats).mapTo[Stats]

  def futureStats: Future[List[Stats]] = Future.sequence(List(askStats("/user/heimdall-service-handler"),
    askStats("/user/admin-service-handler")))

  def restartService: Future[Boolean] = Future {
    getActor("/user/heimdall-service-handler") ! ServiceHandlerUnbind
    getActor("/user/heimdall-service-handler") ! ServiceHandlerBind(settings.proxyHost, settings.proxyPort)
    true
  }

  def receive = runRoute(myRoute)

  val myRoute =
    path("server-stats") {
      get {
        onSuccess(futureStats) { l: List[Stats] =>
          complete {
            views.html.admin.stats(l)
          }
        }
      }
    } ~
    path("restart-service") {
      onSuccess(restartService) { succes: Boolean =>
        redirect(homeUrl, StatusCodes.TemporaryRedirect)
      }
    } ~
    path("") {
      cache(simpleCache) {
        complete {
          views.html.admin.index(settings)
        }
      }
    }
}