package ch.mibex.bitbucket.sonar.client

import java.net.{HttpURLConnection, InetSocketAddress, URL}
import javax.ws.rs.core.MediaType

import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig}
import ch.mibex.bitbucket.sonar.utils.{JsonUtils, LogUtils}
import java.net.Proxy
import com.sun.jersey.api.client.config.{ClientConfig, DefaultClientConfig}
import com.sun.jersey.api.client.filter.LoggingFilter
import com.sun.jersey.api.client.{Client, ClientResponse, UniformInterfaceException}
import com.sun.jersey.client.urlconnection.{HttpURLConnectionFactory, URLConnectionClientHandler}
import org.slf4j.LoggerFactory
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy

import scala.collection.mutable


case class PullRequest(id: Int, srcBranch: String, srcCommitHash: Option[String], dstCommitHash: Option[String])

// global comments do not have a file path, and file-level comments do not require a line number
case class PullRequestComment(commentId: Int, content: String, line: Option[Int], filePath: Option[String]) {
  val isInline = line.isDefined && filePath.isDefined
}

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class BitbucketClient(config: SonarBBPluginConfig) extends BatchComponent {
  private val PageStartIndex = 1
  private val logger = LoggerFactory.getLogger(getClass)
  private val client = createJerseyClient()
  private val v1Api = createResource("1.0")
  private val v2Api = createResource("2.0")
  private lazy val uuid = getLoggedInUserUUID

  private class ProxyConnectionFactory extends HttpURLConnectionFactory {

    override def getHttpURLConnection(url: URL): HttpURLConnection = {
      if (System.getProperties.containsKey("http.proxyHost")) {
        val proxyHost = System.getProperties.getProperty("http.proxyHost")
        val proxyPort = Integer.getInteger("http.proxyPort", 80)
        logger.warn(LogUtils.f(s"Going to use proxy $proxyHost on port $proxyPort..."))
        val proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort))
        url.openConnection(proxy).asInstanceOf[HttpURLConnection]
      } else {
        url.openConnection().asInstanceOf[HttpURLConnection]
      }
    }

  }

  private def createJerseyClient() = {
    val ch = new URLConnectionClientHandler(new ProxyConnectionFactory())
    val client = new Client(ch, createJerseyConfig())

    if (logger.isDebugEnabled) {
      client.addFilter(new LoggingFilter())
    }

    val authentication = new ClientAuthentication(config)
    authentication.configure(client)
    client
  }

  private def createJerseyConfig() = {
    val jerseyConfig = new DefaultClientConfig()
    jerseyConfig.getProperties.put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, java.lang.Boolean.FALSE)
    jerseyConfig
  }

  private def createResource(apiVersion: String) =
    client.resource(s"https://bitbucket.org/api/$apiVersion/repositories/${config.accountName()}/${config.repoSlug()}")

  def findPullRequestsWithSourceBranch(branchName: String): Seq[PullRequest] = {

    def fetchPullRequestsPage(start: Int): (Option[Int], Seq[PullRequest]) =
      fetchPage("/pullrequests", queryParm = ("state", "OPEN"), f =
        response =>
          for (pullRequest <- response("values").asInstanceOf[Seq[Map[String, Any]]])
            yield {
              val source = pullRequest("source").asInstanceOf[Map[String, Any]]
              val srcHash = Option(source("commit")).map(c => c.asInstanceOf[Map[String, Any]]("hash").asInstanceOf[String])
              val dest = pullRequest("destination").asInstanceOf[Map[String, Any]]
              val dstHash = Option(dest("commit")).map(c => c.asInstanceOf[Map[String, Any]]("hash").asInstanceOf[String])
              val branch = source("branch").asInstanceOf[Map[String, Any]]
              PullRequest(
                id = pullRequest("id").asInstanceOf[Int],
                srcBranch = branch("name").asInstanceOf[String],
                srcCommitHash = srcHash,
                dstCommitHash = dstHash
              )
            },
        pageNr = start
      )
    forEachResultPage(Seq[PullRequest](), (pageStart, pullRequests: Seq[PullRequest]) => {
      val (nextPageStart, newPullRequests) = fetchPullRequestsPage(pageStart)
      (nextPageStart, pullRequests ++ newPullRequests)
    }).filter(_.srcBranch == branchName)

  }

  def findOwnPullRequestComments(pullRequest: PullRequest): Seq[PullRequestComment] = {

    def isFromUs(comment: Map[String, Any]): Boolean =
      comment("user").asInstanceOf[Map[String, Any]]("uuid").asInstanceOf[String] equals uuid

    def fetchPullRequestCommentsPage(start: Int): (Option[Int], Seq[PullRequestComment]) = {
      fetchPage(s"/pullrequests/${pullRequest.id}/comments", f =
        response =>
          for (comment <- response("values").asInstanceOf[Seq[Map[String, Any]]] if isFromUs(comment))
            yield {
              val commentId = comment("id").asInstanceOf[Int]
              val content = comment("content").asInstanceOf[Map[String, Any]]("raw").asInstanceOf[String]
              val filePath = comment.get("inline") map {
                _.asInstanceOf[Map[String, Any]]("path").asInstanceOf[String]
              }
              val line = comment.get("inline") map {
                _.asInstanceOf[Map[String, Any]]("to").asInstanceOf[Int]
              }
              PullRequestComment(
                commentId = commentId,
                content = content,
                filePath = filePath,
                line = line
              )
            },
        pageNr = start
      )
    }

    forEachResultPage(Seq[PullRequestComment](), (pageStart, pullRequests: Seq[PullRequestComment]) => {
      val (nextPageStart, newPullRequestComments) = fetchPullRequestCommentsPage(pageStart)
      (nextPageStart, pullRequests ++ newPullRequestComments)
    })
  }

  // create manually by
  // curl -v -u YOUR_BITBUCKET_USER https://api.bitbucket.org/2.0/repositories/YOUR_USER_NAME/REPO_SLUG/pullrequests/PULL_REQUEST_ID/diff
  // then copy the URL from the Location Header field in the HTTP response (LOCATION_URL below) and use that with the appended “?context=0” parameter for the second cURL:
  // curl -u YOUR_BITBUCKET_USER LOCATION_URL?context=0
  def getPullRequestDiff(pullRequest: PullRequest): String = {
    // we do not want to use GET and Jersey's auto-redirect here because otherwise the context param
    // is not passed to the new location
    val response = v2Api
      .path(s"/pullrequests/${pullRequest.id}/diff")
      .accept(MediaType.APPLICATION_JSON)
      .`type`(MediaType.APPLICATION_JSON)
      .head()
    response.getStatus match {
      case 302 if response.getHeaders.containsKey("Location") =>
        val redirectTarget = response.getHeaders.getFirst("Location")
        client.resource(redirectTarget)
          .queryParam("context", "0") // as we are not interested in context lines here
          .get(classOf[String])
      case _ =>
        throw new IllegalArgumentException(s"${SonarBBPlugin.PluginLogPrefix} PR diff response: " + response.getStatus)
    }
  }

  def deletePullRequestComment(pullRequest: PullRequest, commentId: Int): Boolean = {
    val response =
      v1Api
        .path(s"/pullrequests/${pullRequest.id}/comments/$commentId")
        .delete(classOf[ClientResponse])
    response.getStatus == 200
  }

  def approve(pullRequest: PullRequest): Unit = {
    try {
      v2Api
        .path(s"/pullrequests/${pullRequest.id}/approve")
        .post()
    } catch {
      case e: UniformInterfaceException if e.getResponse.getStatus == 409 =>
      // has already been approved yet => ignore this
    }
  }

  def unApprove(pullRequest: PullRequest): Unit = {
    try {
      v2Api
        .path(s"/pullrequests/${pullRequest.id}/approve")
        .delete()
    } catch {
      case e: UniformInterfaceException if e.getResponse.getStatus == 404 =>
      // has not been approved yet, so we cannot unapprove yet => ignore this
    }
  }

  def updateReviewComment(pullRequest: PullRequest, commentId: Int, message: String): Unit = {
    v1Api
      .path(s"/pullrequests/${pullRequest.id}/comments/$commentId")
      .`type`(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .entity(JsonUtils.map2Json(Map("content" -> message)))
      .put()
  }

  def createPullRequestComment(pullRequest: PullRequest,
                               message: String,
                               line: Option[Int] = None,
                               filePath: Option[String] = None): Unit = {
    val entity = new mutable.HashMap[String, Any]()
    entity += "content" -> message
    filePath foreach { f =>
      entity += "filename" -> f
      line match {
        case Some(l) if l > 0 =>
          // see https://bitbucket.org/site/master/issues/11925/post-a-new-comment-on-a-changeset-in-a:
          // "commenting on a "green line" requires "line_to", a red line "line_from" and a white line should take
          // either"; but for context (white) lines only line_from works for me
          //TODO once the Bitbucket issue 11925 is resolved, this should also fix #17 on Github
          entity += "line_to" -> l
        case Some(l) if l == 0 =>
          // this is necessary for file-level pull request comments
          entity += "anchor" -> pullRequest.srcCommitHash.getOrElse("")
          entity += "dest_rev" -> pullRequest.dstCommitHash.getOrElse("")
        case _ => logger.warn(LogUtils.f(s"Invalid or missing line number for issue: $message"))
      }
    }
    v1Api
      .path(s"/pullrequests/${pullRequest.id}/comments")
      .`type`(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .entity(JsonUtils.map2Json(entity.toMap))
      .post(classOf[String])
  }

  private def fetchPage[T](path: String,
                           f: Map[String, Any] => T,
                           queryParm: (String, String) = ("", ""),
                           pageNr: Int = PageStartIndex,
                           pageSize: Int = 50): (Option[Int], T) = {
    // 50 is the pull requests resource max pagelen!
    val response = v2Api.path(path)
      .queryParam("page", pageNr.toString)
      .queryParam("pagelen", pageSize.toString)
      .queryParam(queryParm._1, queryParm._2)
      .accept(MediaType.APPLICATION_JSON)
      .`type`(MediaType.APPLICATION_JSON)
      .get(classOf[String])

    val page = JsonUtils.mapFromJson(response)
    val nextPageStart = page.get("next").map(_ => pageNr + 1)
    (nextPageStart, f(page))
  }

  private def forEachResultPage[S, T](startValue: S, f: (Int, S) => (Option[Int], S)) = {
    var result: S = startValue
    var pageStart: Option[Int] = Some(PageStartIndex)
    while (pageStart.isDefined) {
      val (nextPageStart, next) = f(pageStart.get, result)
      result = next
      pageStart = nextPageStart
    }
    result
  }

  private def getLoggedInUserUUID = {
    val response = client
      .resource(s"https://bitbucket.org/api/2.0/user")
      .accept(MediaType.APPLICATION_JSON)
      .get(classOf[String])
    val user = JsonUtils.mapFromJson(response)
    user("uuid").asInstanceOf[String]
  }

}
