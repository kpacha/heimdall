package io.github.kpacha.heimdall

import akka.actor.{ActorSystem, Props}

object Heimdall extends App {
  implicit val system = ActorSystem("heimdall")
  val settings = Settings(system)

  val adminService = system.actorOf(Props[AdminServiceHandler], "admin-service-handler")
  val heimdallService = system.actorOf(Props[HeimdallServiceHandler], "heimdall-service-handler")

  adminService ! ServiceHandlerBind(settings.adminHost, settings.adminPort)
  heimdallService ! ServiceHandlerBind(settings.proxyHost, settings.proxyPort)
}
