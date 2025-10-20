/*
 * AWS CodeArtifact Gradle Plugin
 * Provides automatic authentication and repository configuration for AWS CodeArtifact
 */
package io.github.taoxiaonj.aws.codeartifact

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import groovy.time.TimeCategory
import java.text.SimpleDateFormat

/**
 * AWS CodeArtifact Gradle Plugin
 * Automatically configures AWS CodeArtifact repositories with SSO token authentication
 */
class AwsCodeArtifactGradlePluginPlugin implements Plugin<Project> {
    static final String HOME_DIR = System.getProperty('user.home')
    static final int CACHE_EXPIRE_HOURS = 6
    static final String TIMESTAMP_PATTERN = 'yyyyMMdd-HHmmss'

    private static String getSsoCacheFilePath(String localProfile) {
        return "${HOME_DIR}/.gradle/awsCodeArtifact/${localProfile}/ssoToken.records"
    }


    /**
     * The plugin's {@code apply} method runs <b>before</b> Gradle evaluates the {@code awsCodeArtifact { ... }}
     * configuration block in your build.gradle file.<BR><BR>
     *
     * The actual values are only available later, which is why the main logic is correctly placed inside a
     * {@code project.afterEvaluate} block.
     * */
    void apply(Project project) {
        project.logger.info("   >>> [${project.name}] 🚀  Applying plugin 'awsCodeArtifact' ...")

        // Create extension for configuration
        def extension = project.extensions.create('awsCodeArtifact', AwsCodeArtifactExtension, project)

        project.afterEvaluate {
            configureRepositories(project, extension)
        }
    }


    private static void createCacheFolder(Project project, String localProfile) {
        def file = new File(getSsoCacheFilePath(localProfile))
        def parent = file.getParentFile()

        if (parent != null && !parent.exists()) {
            parent.mkdirs()
            project.logger.info("   >>> [${project.name}] Cache file directory is created: ${parent.getAbsolutePath()}")
        }
    }


    /**
     * Suppose {@code repoUrl} is like {@code "https://aa-bb-cc-12345.d.codeartifact.us-west-2.amazonaws.com/maven/maven-xyz/"},
     * we will pase {@code "aa-bb-cc"} as {@code domain}, {@code "12345"} as {@code account}, and {@code "us-west-2"} as {@code region}.
     * */
    private static CodeArtifactRepoComponents parseRepoUrl(Project project, String repoUrl) {
        def pattern = /https:\/\/([a-zA-Z0-9-]+)-(\d+)\.d\.codeartifact\.([a-z0-9-]+)\.amazonaws\.com.*/
        def matcher = (repoUrl =~ pattern)

        if (matcher.matches()) {
            def domain = matcher[0][1]    // cfex-infra
            def account = matcher[0][2]   // 538420205323
            def region = matcher[0][3]    // us-west-2

            project.logger.info("   >>> [${project.name}] Parsing repoUrl succeeded: domain = '${domain}', account = '${account}', region = '${region}'")
            return new CodeArtifactRepoComponents(domain: domain, account: account, region: region)
        }

        throw new IllegalArgumentException("[${project.name}] Failed parsing repoUrl '${repoUrl}'")
    }

    
    private void configureRepositories(Project project, AwsCodeArtifactExtension extension) {
        def repoUrl = extension.repoUrl

        if (!repoUrl) {
            throw new IllegalArgumentException("[${project.name}] repoUrl is not provided")
        }

        def parseRepoUrl = parseRepoUrl(project, repoUrl)

        def domain = parseRepoUrl.domain
        def domainOwner = parseRepoUrl.account
        def region = parseRepoUrl.region
        def localProfile = extension.localProfile
        def cacheExpireHours = extension.cacheExpireHours ? extension.cacheExpireHours : CACHE_EXPIRE_HOURS

        createCacheFolder(project, localProfile)

        project.repositories.maven { MavenArtifactRepository repo ->
            repo.url = repoUrl
            repo.credentials {
                username = "aws"
                password = getSsoToken(project, domain, domainOwner, region, localProfile, cacheExpireHours)
            }
        }
    }


    private String getSsoToken(Project project, String domain, String domainOwner, String region, String localProfile, Integer cacheExpireHours) {
        project.logger.info("   >>> [${project.name}] Start loading SSO token ...")

        if (isRunByCircleCi()) {
            return fetchSsoToken(project, domain, domainOwner, region)
        }

        handleSsoLogin(project, localProfile)

        def cachedToken = readCachedSsoToken(project, cacheExpireHours, localProfile)
        if (cachedToken != null) {
            project.logger.info("   >>> [${project.name}] ✅ Cached SSO token is available, will use it")
            return cachedToken
        }

        project.logger.info("   >>> [${project.name}] Retrieving new SSO token ...")

        def tokenValue = fetchSsoToken(project, domain, domainOwner, region, localProfile)
        
        saveSSOTokenToCacheFile(project, tokenValue, localProfile)
        return tokenValue
    }


    private static boolean isRunByCircleCi() {
        return System.getenv("CIRCLECI") == "true"
    }


    /**
     * Check if the caller identity by the given profile:
     * <ul>
     *     <li>if already logged in, skip</li>
     *     <li>otherwise, will try login in the default browser, and then invalidate the token cache</li>
     * </ul>
     * */
    private static void handleSsoLogin(Project project, String localProfile) {
        project.logger.info("   >>> [${project.name}] Checking SSO login status ....")

        def process = [
            "aws", "sts", "get-caller-identity",
            "--profile", localProfile
        ].execute()

        process.waitFor()

        def exitValue = process.exitValue()
        if (exitValue != 0) {
            project.logger.warn("   >>> [${project.name}]  ⚠️  SSO Login status expired, will refresh ...")
            runAwsSsoLogin(project, localProfile)
            invalidateTokenInCacheFile(project, localProfile)
        } else {
            project.logger.info("   >>> [${project.name}] ✅ Already logged-in by profile '${localProfile}'")
        }
    }

    
    private String readCachedSsoToken(Project project, Integer cacheExpireHours, String localProfile) {
        def file = new File(getSsoCacheFilePath(localProfile))
        if (!file.exists()) {
            project.logger.info("   >>> [${project.name}] Local SSO cache does not exist")
            return null
        }
        
        def lines = file.readLines()
        if (lines.isEmpty()) {
            project.logger.info("   >>> [${project.name}] Local SSO cache is empty")
            return null
        }
        
        def lastLine = lines.last()
        if (lastLine.isBlank()) {
            // don't ignore or trim the last blank line
            // it is a signal that we have just re-logged in and need to refetch token again
            project.logger.info("   >>> [${project.name}] Last line of local SSO cache is blank")
            return null
        }
        
        def tokens = lastLine.split(' ')
        try {
            def cachedTime = new SimpleDateFormat(TIMESTAMP_PATTERN).parse(tokens[0])
            def currentTime = new Date()
            
            use(TimeCategory) {
                if (currentTime.after(cachedTime + cacheExpireHours.hours)) {
                    project.logger.info("   >>> [${project.name}] Local SSO cache expires, timestamp = ${cachedTime.format(TIMESTAMP_PATTERN)}")
                    return null
                }
                
                return tokens[tokens.size() - 1]
            }
        } catch (Exception e) {
            project.logger.warn("   >>> [${project.name}] Failed to parse cached SSO token: ${e.message}")
            return null
        }
    }


    private static void saveSSOTokenToCacheFile(Project project, String token, String localProfile) {
        def currentTime = new Date().format(TIMESTAMP_PATTERN)
        project.logger.info("   >>> [${project.name}] Caching SSO token with timestamp $currentTime")
        
        def file = new File(getSsoCacheFilePath(localProfile))
        file.append("\n$currentTime $token")
    }


    private static void invalidateTokenInCacheFile(Project project, String localProfile) {
        project.logger.info("   >>> [${project.name}] Invalidating token cache file ...")

        def file = new File(getSsoCacheFilePath(localProfile))
        file.append("\n\n")
    }

    
    private static String fetchSsoToken(Project project, String domain, String domainOwner, String region, String localProfile) {
        project.logger.info("   >>> [${project.name}] Fetching SSO token with profile '${localProfile}' ...")
        
        def process = [
            "aws", "codeartifact", "get-authorization-token",
            "--domain", domain,
            "--domain-owner", domainOwner,
            "--query", "authorizationToken",
            "--output", "text",
            "--region", region,
            "--profile", localProfile
        ].execute()
        
        process.waitFor()

        def exitValue = process.exitValue()
        if (exitValue != 0) {
            project.logger.error("   >>> [${project.name}] ❌ Failed fetching AWS CodeArtifact token (exitValue = ${exitValue}): ${process.errorStream.text}")
        }
        
        project.logger.info("   >>> [${project.name}] ✅ Successfully fetched SSO token")
        return process.text.trim()
    }


    private static void runAwsSsoLogin(Project project, String localProfile) {
        project.logger.lifecycle("   >>> [${project.name}] Opening SSO authorization page in your default browser ....")

        def process = [
                "aws", "sso", "login",
                "--profile", localProfile
        ].execute()

        process.waitFor()

        def exitValue = process.exitValue()
        if (exitValue != 0) {
            throw new RuntimeException("   >>> [${project.name}] ❌ Failed refreshing AWS SSO token (exitValue = ${exitValue}): ${process.errorStream.text}")
        }

        project.logger.lifecycle("   >>> [${project.name}] ✅ Successfully refreshed AWS SSO token")
    }


    private static String fetchSsoToken(Project project, String domain, String domainOwner, String region) {
        project.logger.info("   >>> [${project.name}] Fetching SSO token without profile ...")
        
        def process = [
            "aws", "codeartifact", "get-authorization-token",
            "--domain", domain,
            "--domain-owner", domainOwner,
            "--query", "authorizationToken",
            "--output", "text",
            "--region", region
        ].execute()
        
        process.waitFor()
        if (process.exitValue() != 0) {
            throw new RuntimeException("   >>> [${project.name}] Failed fetching AWS CodeArtifact token: ${process.errorStream.text}")
        }
        
        project.logger.info("   >>> [${project.name}] ✅ Successfully fetched SSO token")
        return process.text.trim()
    }
}
