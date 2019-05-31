package ch.mibex.bitbucket.sonar.client

import javax.ws.rs.core.{HttpHeaders, MediaType}

import ch.mibex.bitbucket.sonar.utils.JsonUtils
import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig}
import com.sun.jersey.api.client.filter.{ClientFilter, HTTPBasicAuthFilter}
import com.sun.jersey.api.client.{Client, ClientRequest, ClientResponse}
import com.sun.jersey.core.util.MultivaluedMapImpl


// either use the app password from the user like this:
// curl -u teamname:pw -v -X POST --data "content=greetings from sonar" https://bitbucket.org/api/1.0/repositories/accountName/repoSlug/pullrequests/1/comments
//
// or otherwise OAuth:
//
// see here: https://answers.atlassian.com/questions/25133960/answers/25134496/comments/25134581
// First create an access key: curl -v -u "clientToken:clientSecret" https://bitbucket.org/site/oauth2/access_token  -d grant_type=client_credentials
// Then do the request with that key:
// curl -v -X POST --data "content=test comment" -H "Authorization: Bearer oauth_token" https://bitbucket.org/api/1.0/repositories/accountName/repoSlug/pullrequests/1/comments
trait BitbucketAuthentication {

  def createAuthFilter(pluginConfig: SonarBBPluginConfig): ClientFilter

}

trait TeamApiKeyAuthentication extends BitbucketAuthentication {

  override def createAuthFilter(pluginConfig: SonarBBPluginConfig): ClientFilter =
    new HTTPBasicAuthFilter(pluginConfig.teamName(), pluginConfig.apiKey())

}

trait UserOauthAuthentication extends BitbucketAuthentication {
  var oauthAccessToken: String = _

  override def createAuthFilter(pluginConfig: SonarBBPluginConfig): ClientFilter = {
    new ClientFilter() {
      override def handle(request: ClientRequest): ClientResponse = {
        import scala.collection.JavaConversions._
        val headers = request.getHeaders
        headers.put(HttpHeaders.AUTHORIZATION, List(s"Bearer $oauthAccessToken"))
        getNext.handle(request)
      }
    }
  }

}

class AuthenticationBinder {
  self: BitbucketAuthentication =>

  def bind(client: Client, pluginConfiguration: SonarBBPluginConfig): Unit = {
    val filter = createAuthFilter(pluginConfiguration)
    client.addFilter(filter)
  }

}

class ClientAuthentication(config: SonarBBPluginConfig) {

  def configure(client: Client): Unit = {
    if (isUserOauth) {
      val auth = new AuthenticationBinder with UserOauthAuthentication {
        oauthAccessToken = createOauthAccessToken(client)
      }
      auth.bind(client, config)
    } else if (isTeamApiKey) {
      val auth = new AuthenticationBinder with TeamApiKeyAuthentication
      auth.bind(client, config)
    } else if (config.isEnabled) {
      throw new IllegalStateException(
        s"${SonarBBPlugin.PluginLogPrefix} Either team-based API or an OAuth user must be given"
      )
    }
  }

  private def isTeamApiKey = Option(config.teamName()).nonEmpty

  private def isUserOauth = Option(config.oauthTokenClientKey()).nonEmpty

  private def createOauthAccessToken(client: Client) = {
    val oauthFilter = new HTTPBasicAuthFilter(config.oauthTokenClientKey(), config.oauthTokenClientSecret())

    try {
      client.addFilter(oauthFilter)
      val formData = new MultivaluedMapImpl()
      formData.add("grant_type", "client_credentials")
      val response = client
        .resource("https://bitbucket.org/site/oauth2/access_token")
        .`type`(MediaType.APPLICATION_FORM_URLENCODED)
        .post(classOf[ClientResponse], formData)
      val authDetails = response.getEntity(classOf[String])
      JsonUtils.mapFromJson(authDetails).get("access_token") match {
        case Some(access_token: String) => access_token
        case _ =>
          val errorDescription = JsonUtils.mapFromJson(authDetails).getOrElse("error_description", "Unknown error")
          throw new IllegalStateException(
            s"""${SonarBBPlugin.PluginLogPrefix} Could not create an OAuth access token:
                |$errorDescription
                |Have you defined a callback URL in the Bitbucket OAuth configuration?""".stripMargin
          )
      }
    } finally {
      client.removeFilter(oauthFilter)
    }
  }

}
