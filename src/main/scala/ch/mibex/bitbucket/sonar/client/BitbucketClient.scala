package ch.mibex.bitbucket.sonar.client

import javax.ws.rs.core.MediaType

import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig}
import ch.mibex.bitbucket.sonar.utils.{LogUtils, JsonUtils}
import com.sun.jersey.api.client.config.{ClientConfig, DefaultClientConfig}
import com.sun.jersey.api.client.filter.LoggingFilter
import com.sun.jersey.api.client.{Client, ClientResponse, UniformInterfaceException}
import org.slf4j.LoggerFactory
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy

import scala.collection.mutable


case class PullRequest(id: Int, srcBranch: String, srcCommitHash: String, dstCommitHash: String)

// global comments do not have a file path, and file-level comments do not require a line number
case class PullRequestComment(commentId: Int, content: String, line: Option[Int], filePath: Option[String]) {
  val isInline = line.isDefined && filePath.isDefined
}

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class BitbucketClient(config: SonarBBPluginConfig) extends BatchComponent {
  private val logger = LoggerFactory.getLogger(getClass)
  private val client = createJerseyClient()
  private val v1Api = createResource("1.0")
  private val v2Api = createResource("2.0")
  private val PageStartIndex = 1

  private def createJerseyClient() = {
    val client = Client.create(createJerseyConfig())
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

  private def createResource(apiVersion: String) = {
    client.resource(s"https://bitbucket.org/api/$apiVersion/repositories/${config.accountName()}/${config.repoSlug()}")
  }

  def findPullRequestsWithSourceBranch(branchName: String): Seq[PullRequest] = {

    def fetchPullRequestsPage(start: Int): (Option[Int], Seq[PullRequest]) =
      fetchPage("/pullrequests", queryParm = ("state", "OPEN"), f =
        response =>
          for (pullRequest <- response("values").asInstanceOf[Seq[Map[String, Any]]])
            yield {
              val source = pullRequest("source").asInstanceOf[Map[String, Any]]
              val srcCommitHash = source("commit").asInstanceOf[Map[String, Any]]("hash").asInstanceOf[String]
              val dest = pullRequest("destination").asInstanceOf[Map[String, Any]]
              val dstCommitHash = dest("commit").asInstanceOf[Map[String, Any]]("hash").asInstanceOf[String]
              val branch = source("branch").asInstanceOf[Map[String, Any]]
              PullRequest(id = pullRequest("id").asInstanceOf[Int],
                          srcBranch = branch("name").asInstanceOf[String],
                          srcCommitHash = srcCommitHash,
                          dstCommitHash = dstCommitHash)
            },
        pageNr = start
      )
    forEachResultPage(Seq[PullRequest](), (pageStart, pullRequests: Seq[PullRequest]) => {
      val (nextPageStart, newPullRequests) = fetchPullRequestsPage(pageStart)
      (nextPageStart, pullRequests ++ newPullRequests)
    }).filter(_.srcBranch == branchName)

  }

  def findOwnPullRequestComments(pullRequest: PullRequest): Seq[PullRequestComment] = {

    def isFromUs(comment: Map[String, Any]): Boolean = {
      val userName = Option(config.teamName()) match {
        case Some(_) => config.teamName()
        case None => config.accountName()
      }
      comment("user").asInstanceOf[Map[String, Any]]("username").asInstanceOf[String] equals userName
    }

    def fetchCommentsPage(start: Int): (Option[Int], Seq[PullRequestComment]) =
      fetchPage(s"/pullrequests/${pullRequest.id}/comments",
        response =>
          for (comment <- response("values").asInstanceOf[Seq[Map[String, Any]]] if isFromUs(comment))
            yield {
              val pathAndLine = for {
                inline <- comment.get("inline") map { _.asInstanceOf[Map[String, Any]] }
                filePath <- inline.get("path") map { _.asInstanceOf[String] }
                line <- inline.get("to") map { _.asInstanceOf[Int] }
              } yield (filePath, line)
              PullRequestComment(
                commentId = comment("id").asInstanceOf[Int],
                content = comment("content").asInstanceOf[Map[String, Any]]("raw").asInstanceOf[String],
                filePath = pathAndLine.map { _._1 },
                line = pathAndLine.map { _._2 }
              )
            },
        pageNr = start
      )
    forEachResultPage(Seq[PullRequestComment](), (pageStart, comments: Seq[PullRequestComment]) => {
      val (nextPageStart, newComments) = fetchCommentsPage(pageStart)
      (nextPageStart, comments ++ newComments)
    })
  }

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
          entity += "anchor" -> pullRequest.srcCommitHash
          entity += "dest_rev" -> pullRequest.dstCommitHash
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
                           pageSize: Int = 50): (Option[Int], T) = { // 50 is the pull requests resource max pagelen!
    val response = v2Api.path(path)
      .queryParam("page", pageNr.toString)
      .queryParam("pagelen", pageSize.toString)
      .queryParam(queryParm._1, queryParm._2)
      .accept(MediaType.APPLICATION_JSON)
      .`type`(MediaType.APPLICATION_JSON)
      .get(classOf[String])
    val page = JsonUtils.mapFromJson(response)
    val nextPageStart = page.get("next").map(p => pageNr + 1)
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

}
