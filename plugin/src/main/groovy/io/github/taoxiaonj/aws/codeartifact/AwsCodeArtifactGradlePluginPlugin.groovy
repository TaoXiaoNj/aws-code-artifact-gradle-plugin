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
    static final String SSO_CACHE_FILE = HOME_DIR + '/.gradle/awsCodeArtifact/ssoToken.records'
    static final int CACHE_EXPIRE_HOURS = 4
    static final String TIMESTAMP_PATTERN = 'yyyyMMdd-HHmmss'

    /**
     * The plugin's {@code apply} method runs <b>before</b> Gradle evaluates the {@code awsCodeArtifact { ... }}
     * configuration block in your build.gradle file.<BR><BR>
     *
     * The actual values are only available later, which is why the main logic is correctly placed inside a
     * {@code project.afterEvaluate} block.
     * */
    void apply(Project project) {
        project.logger.info("   >>> 🚀  Applying plugin awsCodeArtifact ...")

        createCacheFolder(project)

        // Create extension for configuration
        def extension = project.extensions.create('awsCodeArtifact', AwsCodeArtifactExtension, project)

        project.afterEvaluate {
            configureRepositories(project, extension)
        }
    }


    private static void createCacheFolder(Project project) {
        def file = new File(SSO_CACHE_FILE)
        def parent = file.getParentFile()

        if (parent != null && !parent.exists()) {
            parent.mkdirs()
            project.logger.info("   >>> Cache file directory is created: ${parent.getAbsolutePath()}")
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

            project.logger.info("   >>> Parsing repoUrl succeeded: domain = '${domain}', account = '${account}', region = '${region}'")
            return new CodeArtifactRepoComponents(domain: domain, account: account, region: region)
        }

        throw new IllegalArgumentException("Failed parsing repoUrl '${repoUrl}'")
    }

    
    private void configureRepositories(Project project, AwsCodeArtifactExtension extension) {
        def repoUrl = extension.repoUrl

        if (!repoUrl) {
            throw new IllegalArgumentException("repoUrl is not provided")
        }

        def parseRepoUrl = parseRepoUrl(project, repoUrl)

        def domain = parseRepoUrl.domain
        def domainOwner = parseRepoUrl.account
        def region = parseRepoUrl.region
        def localProfile = extension.localProfile
        def cacheExpireHours = extension.cacheExpireHours ? extension.cacheExpireHours : CACHE_EXPIRE_HOURS

        project.repositories.maven { MavenArtifactRepository repo ->
            repo.url = repoUrl
            repo.credentials {
                username = "aws"
                password = getSsoToken(project, domain, domainOwner, region, localProfile, cacheExpireHours)
            }
        }
    }
    
    private String getSsoToken(Project project, String domain, String domainOwner, String region, String localProfile, Integer cacheExpireHours) {
        project.logger.info("   >>> Start loading SSO token ...")

        def cachedToken = readCachedSsoToken(project, cacheExpireHours)
        if (cachedToken != null) {
            project.logger.info("   >>> ✅ Cached SSO token is available, will use it")
            return cachedToken
        }

        project.logger.info("   >>> Retrieving new SSO token ...")

        def tokenValue = isRunByCircleCi() ? 
            fetchSsoToken(project, domain, domainOwner, region) :
            fetchSsoToken(project, domain, domainOwner, region, localProfile)
        
        saveSSOTokenToCacheFile(project, tokenValue)
        return tokenValue
    }
    
    private static boolean isRunByCircleCi() {
        return System.getenv("CIRCLECI") == "true"
    }
    
    private String readCachedSsoToken(Project project, Integer cacheExpireHours) {
        def file = new File(SSO_CACHE_FILE)
        if (!file.exists()) {
            project.logger.info("   >>> Local SSO cache does not exist")
            return null
        }
        
        def lines = file.readLines()
        if (lines.isEmpty()) {
            project.logger.info("   >>> Local SSO cache is empty")
            return null
        }
        
        def lastLine = lines.last()
        if (lastLine.isBlank()) {
            project.logger.info("   >>> Last line of local SSO cache is blank")
            return null
        }
        
        def tokens = lastLine.split(' ')
        try {
            def cachedTime = new SimpleDateFormat(TIMESTAMP_PATTERN).parse(tokens[0])
            def currentTime = new Date()
            
            use(TimeCategory) {
                if (currentTime.after(cachedTime + cacheExpireHours.hours)) {
                    project.logger.info("   >>> Local SSO cache expires, timestamp = ${cachedTime.format(TIMESTAMP_PATTERN)}")
                    return null
                }
                
                return tokens[tokens.size() - 1]
            }
        } catch (Exception e) {
            project.logger.warn("   >>> Failed to parse cached SSO token: ${e.message}")
            return null
        }
    }


    private static void saveSSOTokenToCacheFile(Project project, String token) {
        def currentTime = new Date().format(TIMESTAMP_PATTERN)
        project.logger.info("   >>> Caching SSO cache with timestamp $currentTime")
        
        def file = new File(SSO_CACHE_FILE)
        file.append("\n$currentTime $token")
    }

    
    private static String fetchSsoToken(Project project, String domain, String domainOwner, String region, String localProfile) {
        project.logger.info("   >>> Fetching SSO token with profile '${localProfile}' ...")
        
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
        if (process.exitValue() != 0) {
            throw new RuntimeException("   >>> Failed fetching AWS CodeArtifact token: ${process.errorStream.text}")
        }
        
        project.logger.info("   >>> ✅ Successfully fetched SSO token")
        return process.text.trim()
    }


    private static String fetchSsoToken(Project project, String domain, String domainOwner, String region) {
        project.logger.info("   >>> Fetching SSO token without profile ...")
        
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
            throw new RuntimeException("   >>> Failed fetching AWS CodeArtifact token: ${process.errorStream.text}")
        }
        
        project.logger.info("   >>> ✅ Successfully fetched SSO token")
        return process.text.trim()
    }
}
