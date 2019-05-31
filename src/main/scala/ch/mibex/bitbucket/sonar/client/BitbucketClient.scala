package ch.mibex.bitbucket.sonar.client

import java.net.{HttpURLConnection, InetSocketAddress, Proxy, URL}

import ch.mibex.bitbucket.sonar.utils.{JsonUtils, LogUtils}
import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig}
import com.sun.jersey.api.client.config.{ClientConfig, DefaultClientConfig}
import com.sun.jersey.api.client.filter.LoggingFilter
import com.sun.jersey.api.client.{Client, ClientResponse, UniformInterfaceException}
import com.sun.jersey.client.urlconnection.{HttpURLConnectionFactory, URLConnectionClientHandler}
import javax.ws.rs.core.MediaType
import org.sonar.api.batch.{InstantiationStrategy, ScannerSide}
import org.sonar.api.batch.rule.Severity
import org.sonar.api.utils.log.Loggers


case class PullRequest(id: Int,
                       srcBranch: String,
                       srcCommitHref: Option[String],
                       srcCommitHash: Option[String],
                       dstCommitHash: Option[String])

// global comments do not have a file path, and file-level comments do not require a line number
case class PullRequestComment(commentId: Int, content: String, line: Option[Int], filePath: Option[String]) {
  val isInline = line.isDefined && filePath.isDefined
}

sealed trait BuildStatus {
  def name: String
  def description: String
}
case class FailingBuildStatus(severity: Severity, numIssues: Int) extends BuildStatus {
  val name = "FAILED"
  val description = s"Sonar analysis failed. Found $numIssues issues with severity >= $severity"
}

case object InProgressBuildStatus extends BuildStatus {
  val name = "INPROGRESS"
  val description = "Sonar analysis in progress..."
}
case object SuccessfulBuildstatus extends BuildStatus {
  val name = "SUCCESSFUL"
  val description = s"Sonar analysis successful :-)"
}

@ScannerSide
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class BitbucketClient(config: SonarBBPluginConfig) {
  private final val PageStartIndex = 1
  private val logger = Loggers.get(getClass)
  private val client = createJerseyClient()
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
    client.resource(s"https://api.bitbucket.org/$apiVersion/repositories/${config.accountName()}/${config.repoSlug()}")

  private def mapToPullRequest(pullRequest: Map[String, Any]): PullRequest = {
    val source = pullRequest("source").asInstanceOf[Map[String, Any]]
    val commit = source("commit").asInstanceOf[Map[String, Any]]
    val srcHref = Option(commit("links").asInstanceOf[Map[String, Any]]("self").asInstanceOf[Map[String, Any]]("href").asInstanceOf[String])
    val srcHash = Option(commit("hash").asInstanceOf[String])
    val dest = pullRequest("destination").asInstanceOf[Map[String, Any]]
    val dstHash = Option(dest("commit")).map(c => c.asInstanceOf[Map[String, Any]]("hash").asInstanceOf[String])
    val branch = source("branch").asInstanceOf[Map[String, Any]]

    PullRequest(
      id = pullRequest("id").asInstanceOf[Int],
      srcBranch = branch("name").asInstanceOf[String],
      srcCommitHref = srcHref,
      srcCommitHash = srcHash,
      dstCommitHash = dstHash
    )
  }

  def findPullRequestWithId(pullRequestId: Int): Option[PullRequest] = {
    try {
      val response = v2Api.path(s"/pullrequests/$pullRequestId")
        .accept(MediaType.APPLICATION_JSON)
        .`type`(MediaType.APPLICATION_JSON)
        .get(classOf[String])

      val pullRequest = JsonUtils.mapFromJson(response)
      Some(mapToPullRequest(pullRequest))
    } catch {
      case e: UniformInterfaceException if e.getResponse.getStatus == 404 => None
    }
  }

  def findPullRequestsWithSourceBranch(branchName: String): Seq[PullRequest] = {

    def fetchPullRequestsPage(start: Int): (Option[Int], Seq[PullRequest]) =
      fetchPage("/pullrequests", queryParm = ("state", "OPEN"), f =
        response =>
          for (pullRequest <- response("values").asInstanceOf[Seq[Map[String, Any]]])
          yield mapToPullRequest(pullRequest),
        pageNr = start
      )
    forEachResultPage(Seq[PullRequest](), (pageStart, pullRequests: Seq[PullRequest]) => {
      val (nextPageStart, newPullRequests) = fetchPullRequestsPage(pageStart)
      (nextPageStart, pullRequests ++ newPullRequests)
    }).filter(_.srcBranch.toLowerCase == branchName.toLowerCase)

  }

  def findOwnPullRequestComments(pullRequest: PullRequest): Seq[PullRequestComment] = {

    def isFromUs(comment: Map[String, Any]): Boolean =
      Option(comment("user").asInstanceOf[Map[String, Any]])
        .getOrElse(Map("uuid" -> ""))("uuid").asInstanceOf[String] equals uuid

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
      v2Api
        .path(s"/pullrequests/${pullRequest.id}/comments/$commentId")
        .delete(classOf[ClientResponse])
    response.getStatus == 200
  }

  def updateBuildStatus(pullRequest: PullRequest, buildStatus: BuildStatus, sonarServerUrl: String): Unit = {
    try {
      // This uses the source url from the bitbucket response.
      // Typically pullrequests' source is not the same repo as where the PR was created.
      // Usually PR's have a fork as source, which means a different accountName and repoSlug
      val v2SourceApi = client.resource(
        s"${pullRequest.srcCommitHref.getOrElse(v2Api.path(s"/commit/${pullRequest.srcCommitHash.getOrElse("")}"))}"
      )
      v2SourceApi
        .path(s"/statuses/build")
        .`type`(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .entity(JsonUtils.map2Json(Map("state" -> buildStatus.name,
                                       "description" -> buildStatus.description,
                                       "name" -> "Sonar analysis",
                                       "key" -> s"SONAR-ANALYSIS-PR-${pullRequest.id}",
                                       "url" -> sonarServerUrl)))
        .post()
     } catch {
       case e: UniformInterfaceException =>
         throw new IllegalArgumentException(
           s"${SonarBBPlugin.PluginLogPrefix} " +
           s"${pullRequest.srcCommitHref}/statuses/build resulted in error: " + e.getResponse.getStatus
         )
     }
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
    v2Api
      .path(s"/pullrequests/${pullRequest.id}/comments/$commentId")
      .`type`(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .entity(JsonUtils.map2Json(Map("content" -> Map("raw" -> message))))
      .put()
  }

  def createPullRequestComment(pullRequest: PullRequest,
                               message: String,
                               line: Option[Int] = None,
                               filePath: Option[String] = None): Unit = {
    var entity = Map[String, Any]()
    entity += "content" -> Map("raw" -> message)
    filePath foreach { f =>
      var inlineParam = Map[String, Any]()
      inlineParam += "path" -> f
      line match {
        case Some(l) if l > 0 => inlineParam += "to" -> l
        case _ => logger.warn(LogUtils.f(s"Invalid or missing line number for issue: $message"))
      }
      entity += "inline" -> inlineParam
    }

    v2Api
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

  private def getLoggedInUserUUID: String = {
    try {
      val response = client
        .resource(s"https://api.bitbucket.org/2.0/user")
        .accept(MediaType.APPLICATION_JSON)
        .get(classOf[String])
      val user = JsonUtils.mapFromJson(response)
      user("uuid").asInstanceOf[String]
    } catch {
      case e: UniformInterfaceException =>
        throw new IllegalStateException(
          s"${SonarBBPlugin.PluginLogPrefix} Couldn't fetch logged in user uuid, got status: ${e.getResponse.getStatus}"
        )
    }
  }

}
