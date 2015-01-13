package io.github.kpacha.heimdall

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.io.IO
import scala.concurrent.duration._
import spray.can.Http
import io.github.kpacha.heimdall.filter._

case class ServiceHandlerBind(host: String, port: Int)
case class ServiceHandlerUnbind()

trait ServiceHandler extends Actor with ActorLogging {
  import context.system
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: ArithmeticException      => Resume
    case _: NullPointerException     => Restart
    case _: IllegalArgumentException => Stop
    case _: Exception                => Restart
  }
  var child: ActorRef

  def idle: Receive = {
    case ServiceHandlerBind(host, port) => {
      log.info("Starting the service " + child)
      IO(Http) ! Http.Bind(child, host, port = port)
      context.become(working)
    }
    case ServiceHandlerUnbind => log.warning("Trying to stop an already stopped service")
  }

  def working: Receive = {
    case ServiceHandlerUnbind => {
      log.info("Stoping the service " + child)
      context.stop(self)
    }
    case ServiceHandlerBind(_, _) => log.warning("Trying to start an already started service")
  }

  def receive = idle

  def createChild(props : Props, name: String): ActorRef = context.actorOf(props, name)
}

class AdminServiceHandler extends ServiceHandler {
  var child: ActorRef = _

  override def preStart() {
    child = createChild(Props[AdminService], "admin-service")
  }
}

import akka.routing.FromConfig

class HeimdallServiceHandler extends ServiceHandler {
  var child: ActorRef = _
  var proxyRouter: ActorRef = _
  var shadowProxyRouter: ActorRef = _
  var witerRouter: ActorRef = _

  override def preStart() {
    child = createChild(FromConfig.props(Props[HeimdallService]), "heimdall-router")
    proxyRouter = createChild(FromConfig.props(Props[ProxyFilter]), "proxy-router")
    shadowProxyRouter = createChild(FromConfig.props(Props[ShadowProxyForkFilter]), "shadow-proxy-router")
    witerRouter = createChild(FromConfig.props(Props[WriterFilter]), "writer-router")
  }
}