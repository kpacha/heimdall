package io.github.kpacha.heimdall.proxy

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import scala.collection.JavaConversions._
import spray.http.{HttpMessage, HttpEntity, ContentType, MediaType, MediaTypes}
import MediaTypes._

trait DecoratedProxyActor extends ProxyActor {
  val typesToDecorate: List[String] =
    List(`application/javascript`, `application/json`, `text/css`, `application/xhtml+xml`,
      `text/html`) map (_.toString)
  private val prefixToAvoid: List[String] = List("../", "http")

  private def toDecorate(contentType: String)(candidate: String): Boolean =
    contentType.startsWith(candidate)

  private def shouldDecorate(contentType: String): Boolean =
    typesToDecorate exists toDecorate(contentType)

  private def normalizePath(path: String): String =
    if (path.startsWith("/")) path
    else "/" + path

  private def shouldEdit(uri: String, toAvoid: List[String]): Boolean =
    !uri.isEmpty && !(toAvoid exists uri.startsWith)

  private def fix(tag: String, rpprefix: String, toAvoid: List[String])(entity: Element) = {
    val uri = entity.attr(tag)
    if (shouldEdit(uri, toAvoid)) entity.attr(tag, rpprefix + normalizePath(uri))
  }

  private def fixA(rpprefix: String)(entity: Element) =
    fix("href", rpprefix, "#" :: prefixToAvoid)(entity)

  private def fixLink(rpprefix: String)(entity: Element) =
    fix("href", rpprefix, prefixToAvoid)(entity)

  private def fixScript(rpprefix: String)(entity: Element) =
    fix("src", rpprefix, prefixToAvoid)(entity)

  private def fixImage(rpprefix: String)(entity: Element) =
    fix("src", rpprefix, prefixToAvoid)(entity)

  override def mapMessage(msg: HttpMessage, contentType: ContentType, rpprefix: String): HttpMessage = 
    if (shouldDecorate(contentType.toString)) {
      msg.mapEntity { entity =>
        HttpEntity(contentType, {
          val parsed = Jsoup.parse(entity.asString)
          asScalaIterator(parsed.select("a").iterator()).foreach (fixA(rpprefix))
          asScalaIterator(parsed.select("link").iterator()).foreach (fixLink(rpprefix))
          asScalaIterator(parsed.select("script").iterator()).foreach (fixScript(rpprefix))
          asScalaIterator(parsed.select("img").iterator()).foreach (fixImage(rpprefix))
          parsed.html()
        })
      }
    }
    else msg
}