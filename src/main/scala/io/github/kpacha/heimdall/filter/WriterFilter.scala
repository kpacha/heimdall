package io.github.kpacha.heimdall.filter

import akka.actor.{Actor, ActorLogging}
import io.github.kpacha.heimdall.PostProcessedRequest

class WriterFilter extends Actor with ActorLogging {
  import context.system

  def receive = {
    case PostProcessedRequest(analysis, originalsender, offset, _, _, _, res) => {
      val nextFilter = offset + 1
      log.info("[{}] Ending the workflow {} -> [{}] responding to {}",
        offset, analysis.id, analysis.filters.drop(nextFilter), originalsender)
      originalsender ! res
      if(nextFilter != analysis.filters.length)
        log.warning("The writer filter should be the last of your chain!")
    }
  }
}