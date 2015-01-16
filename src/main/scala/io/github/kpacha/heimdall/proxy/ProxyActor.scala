package io.github.kpacha.heimdall.proxy

import spray.httpx.encoding._
import akka.actor._
import spray.util._
import spray.http._
import HttpHeaders.{`Content-Type`, Location}
import MediaTypes._
import io.github.kpacha.heimdall.Settings

trait ProxyActor extends Actor with ActorLogging {
  import context.system
  val headersToAvoid: List[String] = List("Content-Length", "Content-Type", "Date", "Server", "Transfer-Encoding")
  val codec = Gzip({ message: HttpMessage => message.isResponse })

  def mapMessage(msg: HttpMessage, contentType: ContentType, rpprefix: String): HttpMessage = msg

  def processResponse(res: HttpResponse, rpprefix: String): HttpResponse = {
    val contentType = getContentType(res)
    log.debug("CONTENT TYPE: {}", contentType)

    val encoding = res.encoding.value
    val msg = handleBodyEncoding(encoding, codec.decode, res)
    val finalMsg = normalizeHeaders(mapMessage(msg, contentType, rpprefix), rpprefix)
    log.debug("RESPONSE: {}", finalMsg)

    handleBodyEncoding(encoding, codec.encode, finalMsg) match {
      case response: HttpResponse => response
      case r => {
        log.warning("Unknown type {}", r)
        throw new Exception("Unknown type!")
      }
    }
  }

  private def getContentType(res: HttpResponse): ContentType =
    extractContentType(res.headers.filter(contentTypeHeaders))

  private def extractContentType(headers: List[HttpHeader]): ContentType = headers match {
    case `Content-Type`(ct) :: _ => ct
    case _ => ContentType(MediaTypes.`text/html`)
  }

  private def contentTypeHeaders(header: HttpHeader): Boolean = header match {
    case `Content-Type`(ct) => true
    case _ => false
  }

  private def normalizeHeaders(msg: HttpMessage, rpprefix: String): HttpMessage =
    msg.mapHeaders { x =>
      x.filter(h => !headersToAvoid.contains(h.name)) map { 
        case Location(uri) => Location(rpprefix + uri)
        case h => h
      }
    }

  private def handleBodyEncoding(encoding: String, f: HttpMessage => HttpMessage, obj: HttpMessage): HttpMessage =
    encoding match {
      case "gzip" => f(obj)
      case "deflate" => f(obj)
      case _ => obj 
    }
}