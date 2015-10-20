package ch.mibex.bitbucket.sonar.client

import javax.ws.rs.core.{MediaType, MultivaluedMap}

import ch.mibex.bitbucket.sonar.{PluginConfiguration, SonarBitbucketPlugin}
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter
import com.sun.jersey.api.client.{Client, ClientResponse, WebResource}
import com.sun.jersey.core.util.MultivaluedMapImpl
import org.junit.runner.RunWith
import org.mockito
//import org.mockito.Answers.RETURNS_DEEP_STUBS
//import org.mockito.Mockito.withSettings
import org.sonar.api.config.{PropertyDefinitions, Settings}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope



@RunWith(classOf[JUnitRunner])
class ClientAuthenticationSpec extends Specification with Mockito {

  class AuthContext extends Scope {
    val settings = new Settings(new PropertyDefinitions(classOf[SonarBitbucketPlugin]))
    val pluginConfig = new PluginConfiguration(settings)
    val authentication = new ClientAuthentication(pluginConfig)
    val client = mock[Client] //(withSettings.defaultAnswer(RETURNS_DEEP_STUBS.get))
  }

  "bind authentication to Jersey client" should {

    "use a basic auth filter for team API key" in new AuthContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketApiKey, "xxxxxxxxx")
      settings.setProperty(SonarBitbucketPlugin.BitbucketTeamName, "a_team")
      authentication.configure(client)
      there was one(client).addFilter(any[HTTPBasicAuthFilter])
    }

    "use client filter for OAuth communication" in new AuthContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientKey, "xxxxxxxxx")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientSecret, "a_team")

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
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "xxxxxxxxx")
      authentication.configure(client) must throwA[IllegalStateException]
    }

    "do not throw an exception if the plug-in is disabled" in new AuthContext {
      authentication.configure(client)
    }

  }


}