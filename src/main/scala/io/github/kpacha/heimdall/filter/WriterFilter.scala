package io.github.kpacha.heimdall.filter

import akka.actor.{Actor, ActorLogging}
import io.github.kpacha.heimdall.PostProcessedRequest

class WriterFilter extends Actor with ActorLogging {
  import context.system

  def receive = {
  	case PostProcessedRequest(uuid, originalsender, filters, _, _, _, res) => {
  	  log.info("Ending the workflow {} -> [{}] responding to {}", uuid, filters, originalsender)
  	  originalsender ! res
      if(!filters.isEmpty) log.warning("The writer filter should be the last of your chain!")
    }
  }
}