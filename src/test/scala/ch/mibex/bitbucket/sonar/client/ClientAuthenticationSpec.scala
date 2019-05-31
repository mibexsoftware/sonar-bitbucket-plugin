package ch.mibex.bitbucket.sonar.client


import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig}
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter
import com.sun.jersey.api.client.{Client, ClientResponse, WebResource}
import com.sun.jersey.core.util.MultivaluedMapImpl
import javax.ws.rs.core.{MediaType, MultivaluedMap}
import org.junit.runner.RunWith
import org.mockito
import org.sonar.api.config.PropertyDefinitions
import org.sonar.api.config.internal.MapSettings
import org.sonar.api.platform.Server
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope


@RunWith(classOf[JUnitRunner])
class ClientAuthenticationSpec extends Specification with Mockito {

  class AuthContext extends Scope {
    val settings = new MapSettings(new PropertyDefinitions(classOf[SonarBBPlugin]))
    val server = mock[Server]
    val pluginConfig = new SonarBBPluginConfig(settings, server)
    val authentication = new ClientAuthentication(pluginConfig)
    val client = mock[Client] //(withSettings.defaultAnswer(RETURNS_DEEP_STUBS.get))
  }

  "bind authentication to Jersey client" should {

    "use a basic auth filter for APP password" in new AuthContext {
      settings.setProperty(SonarBBPlugin.BitbucketApiKey, "xxxxxxxxx")
      settings.setProperty(SonarBBPlugin.BitbucketTeamName, "a_team")
      authentication.configure(client)
      there was one(client).addFilter(any[HTTPBasicAuthFilter])
    }

    "use client filter for OAuth communication" in new AuthContext {
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientKey, "xxxxxxxxx")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientSecret, "a_team")

      // unfortunately, we cannot use Mockito's deep mocks here as they seam to be broken when using Jersey and
      // its generic POST method; so we hand-mock everything here...
      val response = mock[ClientResponse]
      response.getEntity(classOf[String]) returns
        """{"access_token": "asgagdfhdhd",
          |"scopes": "pullrequest:write account",
          |"expires_in": 3600,
          |"refresh_token": "dsfafsafdf",
          |"token_type": "bearer"}""".stripMargin
      val webResourceBuilder = mock[WebResource#Builder]
      val webResource = mock[WebResource]
      client.resource("https://bitbucket.org/site/oauth2/access_token") returns webResource
      webResource.`type`(MediaType.APPLICATION_FORM_URLENCODED) returns webResourceBuilder
      val formData = new MultivaluedMapImpl()
      formData.add("grant_type", "client_credentials")
      webResourceBuilder.post(
        mockito.Matchers.anyVararg().asInstanceOf[Class[ClientResponse]], any[MultivaluedMap[String, String]]
      ) returns response

      authentication.configure(client)

      there was one(client).resource("https://bitbucket.org/site/oauth2/access_token")
    }

    "abort if no valid authentication is configured and plug-in is enabled" in new AuthContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "xxxxxxxxx")
      authentication.configure(client) must throwA[IllegalStateException]
    }

    "do not throw an exception if the plug-in is disabled" in new AuthContext {
      authentication.configure(client)
    }

  }

}