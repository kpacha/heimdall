package io.github.kpacha.heimdall

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.io.IO
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.duration._
import spray.can.Http
import spray.can.server.Stats
import Http._
import io.github.kpacha.heimdall.filter._

abstract class ServiceHandlerProtocol
case class ServiceHandlerBind(host: String, port: Int) extends ServiceHandlerProtocol
case class ServiceHandlerUnbind() extends ServiceHandlerProtocol

trait ServiceHandler extends Actor with ActorLogging with Stash {
  implicit val timeout: Timeout = 3.second // for the actor 'asks'
  import context.dispatcher // ExecutionContext for the futures and scheduler
  import context.system
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _ => Restart
  }
  var child: ActorRef
  var children: List[ActorRef]

  var host: String = "localhost"
  var port: Int = 80

  var httpListener: ActorRef = null

  def idle: Receive = {
    case ServiceHandlerBind(h, p) => {
      log.info("Starting the service " + child)
      host = h
      port = p
      IO(Http) ! Bind(child, host, port = port)
      unstashAll
      context.become(connecting)
    }
    case ServiceHandlerUnbind => log.warning("Trying to stop an already stopped service")
    case msg: Command => stash
  }

  def connecting: Receive = {
    case _: Bound => {
      log.debug("Service {} is bound!", child.toString)
      httpListener = sender
      unstashAll
      context.become(working)
    }
    case _: CommandFailed => {
      log.warning("CommandFailed message arrived!!!")
      unstashAll
      context.become(idle)
    }
    case msg: ServiceHandlerProtocol => stash
  }

  def working: Receive = {
    case ServiceHandlerUnbind => {
      log.info("Stoping the service " + child)
      httpListener ! Unbind
      children foreach (_ ! Kill)
      unstashAll
      context.become(disconnecting)
    }
    case ServiceHandlerBind(_, _) => log.warning("Trying to start an already started service")
    case Http.GetStats => {
      val originalSender = sender
      ((httpListener ? Http.GetStats).mapTo[Stats]) pipeTo originalSender
    }
    case msg: Command => stash
  }

  def disconnecting: Receive = {
    case Http.Unbound => {
      log.debug("Service {} is unbound!", child.toString)
      httpListener = null
      unstashAll
      context.become(idle)
    }
    case _: CommandFailed => {
      log.warning("CommandFailed message arrived!!!")
      unstashAll
      context.become(idle)
    }
    case msg: ServiceHandlerProtocol => stash
  }

  def receive = idle

  def createChild(props : Props, name: String): ActorRef = context.actorOf(props, name)
}

class AdminServiceHandler extends ServiceHandler {
  var child: ActorRef = _
  var children: List[ActorRef] = _

  override def preStart() {
    child = createChild(Props[AdminService], "admin-service")
    children = List(child)
  }
}

import akka.routing.FromConfig

class HeimdallServiceHandler extends ServiceHandler {
  var child: ActorRef = _
  var proxyRouter: ActorRef = _
  var shadowProxyRouter: ActorRef = _
  var witerRouter: ActorRef = _
  var errorRouter: ActorRef = _
  var children: List[ActorRef] = _

  override def preStart() {
    child = createChild(FromConfig.props(Props[HeimdallService]), "heimdall-router")
    proxyRouter = createChild(FromConfig.props(Props[ProxyFilter]), "proxy-router")
    shadowProxyRouter = createChild(FromConfig.props(Props[ShadowProxyForkFilter]), "shadow-proxy-router")
    witerRouter = createChild(FromConfig.props(Props[WriterFilter]), "writer-router")
    errorRouter = createChild(FromConfig.props(Props[ErrorFilter]), "error-router")
    children = List(child, proxyRouter, shadowProxyRouter, witerRouter, errorRouter)
  }
}