package ch.mibex.bitbucket.sonar.client

import javax.ws.rs.core.{HttpHeaders, MediaType}

import ch.mibex.bitbucket.sonar.PluginConfiguration
import ch.mibex.bitbucket.sonar.utils.JsonUtils
import com.sun.jersey.api.client.filter.{ClientFilter, HTTPBasicAuthFilter}
import com.sun.jersey.api.client.{Client, ClientRequest, ClientResponse}
import com.sun.jersey.core.util.MultivaluedMapImpl


// either use the API key from the team like this:
// curl -u teamname:pw -v -X POST --data "content=greetings from sonar" https://bitbucket.org/api/1.0/repositories/accountName/repoSlug/pullrequests/1/comments
//
// or otherwise OAuth:
//
// see here: https://answers.atlassian.com/questions/25133960/answers/25134496/comments/25134581
// First create an access key: curl -v -u "clientToken:clientSecret" https://bitbucket.org/site/oauth2/access_token  -d grant_type=client_credentials
// Then do the request with that key:
// curl -v -X POST --data "content=test comment" -H "Authorization: Bearer oauth_token" https://bitbucket.org/api/1.0/repositories/accountName/repoSlug/pullrequests/1/comments
trait BitbucketAuthentication {

  def createAuthFilter(pluginConfig: PluginConfiguration): ClientFilter

}

trait TeamApiKeyAuthentication extends BitbucketAuthentication {

  override def createAuthFilter(pluginConfig: PluginConfiguration): ClientFilter =
    new HTTPBasicAuthFilter(pluginConfig.teamName(), pluginConfig.apiKey())

}

trait UserOauthAuthentication extends BitbucketAuthentication {
  var oauthAccessToken: String = _

  override def createAuthFilter(pluginConfig: PluginConfiguration): ClientFilter = {
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

  def bind(client: Client, pluginConfiguration: PluginConfiguration): Unit = {
    val filter = createAuthFilter(pluginConfiguration)
    client.addFilter(filter)
  }

}

class ClientAuthentication(config: PluginConfiguration) {

  def configure(client: Client): Unit = {
    val auth = if (isUserOauth) {
      new AuthenticationBinder with UserOauthAuthentication {
        oauthAccessToken = createOauthAccessToken(client)
      }
    } else if (isTeamApiKey) {
      new AuthenticationBinder with TeamApiKeyAuthentication
    } else {
      throw new IllegalStateException("Either team-based API or an OAuth user authentication has to be used")
    }
    auth.bind(client, config)
  }

  private def isTeamApiKey = Option(config.teamName()).nonEmpty

  private def isUserOauth = Option(config.oauthTokenClientKey()).nonEmpty

  private def createOauthAccessToken(client: Client) = {
    val oauthFilter = new HTTPBasicAuthFilter(config.oauthTokenClientKey(), config.oauthTokenClientSecret())
    client.addFilter(oauthFilter)
    val formData = new MultivaluedMapImpl()
    formData.add("grant_type", "client_credentials")
    val response = client
      .resource("https://bitbucket.org/site/oauth2/access_token")
      .`type`(MediaType.APPLICATION_FORM_URLENCODED)
      .post(classOf[ClientResponse], formData)
    val authDetails = response.getEntity(classOf[String])
    client.removeFilter(oauthFilter)
    JsonUtils.mapFromJson(authDetails).get("access_token") match {
      case Some(access_token: String) => access_token
      case _ => throw new IllegalStateException("No access token found")
    }
  }

}
