# Bitbucket plug-in for SonarQube

![Travis build status](https://travis-ci.org/mibexsoftware/sonar-bitbucket-plugin.svg?branch=master)

This SonarQube plug-in creates pull request comments for issues found in your Bitbucket pull requests. It is very
similar and inspired by the [SonarQube Github plug-in](https://github.com/SonarCommunity/sonar-github), but targets 
Bitbucket cloud. It creates a summary of the found issues as a global pull request comment which looks like this:

![Screenshot global pull request comment plugin](doc/global-comment.png)

For every found issues on changed or new lines of the pull request, it will also create a pull request comment with
the severity, the explanation what this issue is about and a link to get more details about it:

![Screenshot global pull request comment plugin](doc/example-issue.png)

## Usage

### Prerequisites
- SonarQube 4.5.x
- A Bitbucket account
- Maven 3.x + JDK 1.7 (to manually build it)

### Installation

The plug-in will probably once be available in the SonarQube update center. Until then, you can download it from our 
[Github releases page](https://github.com/mibexsoftware/sonar-bitbucket-plugin/releases/latest).

If you want, you can also build the plug-in manually like follows:

```
mvn clean install
```

After having copied the plugin's JAR in `{SONARQUBE_INSTALL_DIRECTORY}/extensions/plugins`, you need to restart your
SonarQube instance.

### Configuration Jenkins

You need to run this plug-in as part of your build. Add a build step of type `Execute shell` to your Jenkins job with
the following content:
 
```
mvn clean verify sonar:sonar --batch-mode --errors \
     -Dsonar.bitbucket.repoSlug=YOUR_BITBUCKET_REPO_SLUG \
     -Dsonar.bitbucket.accountName=YOUR_BITBUCKET_ACCOUNT_NAME \
     -Dsonar.bitbucket.teamName=YOUR_BITBUCKET_TEAM_NAME \
     -Dsonar.bitbucket.apiKey=YOUR_BITBUCKET_API_KEY \
     -Dsonar.bitbucket.branchName=$GIT_BRANCH \
     -Dsonar.host.url=http://YOUR_SONAR_SERVER \
     -Dsonar.login=YOUR_SONAR_LOGIN \
     -Dsonar.password=YOUR_SONAR_PASSWORD \
     -Dsonar.analysis.mode=incremental
```
 
See this table about the possible configuration options:


| Parameter name                               | Description                                                                                                                                                                                                                    | Default value                                  | Example                |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|------------------------|
| sonar.bitbucket.repoSlug                     | The slug of your Bitbucket repository (https://bitbucket.org/[account_name]/[repo_slug]).                                                                                                                                      |                                                | sonar-bitbucket-plugin |
| sonar.bitbucket.accountName                  | The Bitbucket account your repository belongs to (https://bitbucket.org/[account_name]/[repo_slug]).                                                                                                                           |                                                | mibexsoftware          |
| sonar.bitbucket.teamName                     | If you want to create pull request comments for Sonar issues under your team account, provide the team name here.                                                                                                              |                                                | a_team                 |
| sonar.bitbucket.apiKey                       | If you want to create pull request comments for Sonar issues under your team account, provide the API key for your team account here.                                                                                          |                                                |                        |
| sonar.bitbucket.oauthClientKey               | If you want to create pull request comments for Sonar issues under your personal account provide the client key of the OAuth consumer created for this application here (needs Repository and pull request WRITE permissions). |                                                |                        |
| sonar.bitbucket.oauthClientSecret            | If you want to create pull request comments for Sonar issues under your personal account, provide the OAuth client secret for this application here.                                                                           |                                                |                        |
| sonar.bitbucket.branchName                   | The branch name you want to get analyzed with SonarQube. When building with Jenkins, use $GIT_BRANCH. For Bamboo, you can use ${bamboo.repository.git.branch}.                                                                 |                                                | $GIT_BRANCH            |
| sonar.bitbucket.branchIllegalCharReplacement | If you are using SonarQube version <= 4.5, then you have to escape '/' in your branch names with another character. Please provide this replacement character here.                                                            |                                                | _                      |
| sonar.bitbucket.minSeverity                  | se either INFO, MINOR, MAJOR, CRITICAL or BLOCKER to only have pull request comments created for issues with severities greater or equal to this one.                                                                          | MAJOR                                          |                        |


For authentication, you have to decide between if you want to create pull requests as your user by OAuth or as your
team account with an API key. If you have a team account, we suggest to use this one as it is less confusing if the
issues are created by this account opposed to when a personal account is taken. Unfortunately, Bitbucket does not 
(yet) support technical users as GitHub does, so we have to use either a user or team account here.

After the next commit, you should be able to see comments in your pull request on Bitbucket.