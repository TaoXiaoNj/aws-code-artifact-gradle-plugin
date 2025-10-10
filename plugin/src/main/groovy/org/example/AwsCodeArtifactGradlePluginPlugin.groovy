/*
 * AWS CodeArtifact Gradle Plugin
 * Provides automatic authentication and repository configuration for AWS CodeArtifact
 */
package org.example

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import groovy.time.TimeCategory

/**
 * AWS CodeArtifact Gradle Plugin
 * Automatically configures AWS CodeArtifact repositories with SSO token authentication
 */
class AwsCodeArtifactGradlePluginPlugin implements Plugin<Project> {
    
    static final String SSO_CACHE_FILE = '.ssoToken.records'
    static final int CACHE_EXPIRE_HOURS = 4
    static final String TIMESTAMP_PATTERN = 'yyyyMMdd-HHmmss'
    
    void apply(Project project) {
        // Create extension for configuration
        def extension = project.extensions.create('awsCodeArtifact', AwsCodeArtifactExtension, project)
        
        // Set default values
        extension.localProfile = extension.localProfile ?: 'infra'
        extension.region = extension.region ?: 'us-west-2'
        extension.cacheExpireHours = extension.cacheExpireHours ?: CACHE_EXPIRE_HOURS
        
        project.afterEvaluate {
            configureRepositories(project, extension)
        }
    }
    
    private void configureRepositories(Project project, AwsCodeArtifactExtension extension) {
        if (!extension.repoUrl) {
            project.logger.warn("AWS CodeArtifact repository URL not configured. Skipping repository configuration.")
            return
        }
        
        def repoUrl = extension.repoUrl
        def domain = extension.domain
        def domainOwner = extension.domainOwner
        def region = extension.region
        def localProfile = extension.localProfile
        
        project.repositories.maven { MavenArtifactRepository repo ->
            repo.url = repoUrl
            repo.credentials {
                username = "aws"
                password = getSsoToken(project, domain, domainOwner, region, localProfile, extension.cacheExpireHours)
            }
        }
    }
    
    private String getSsoToken(Project project, String domain, String domainOwner, String region, String localProfile, int cacheExpireHours) {
        def cachedToken = readCachedSsoToken(project)
        if (cachedToken != null) {
            project.logger.info("Using cached SSO token")
            return cachedToken
        }
        
        def tokenValue = isRunByCircleCi() ? 
            loadCircleCiSsoToken(project, domain, domainOwner, region) : 
            loadLocalSsoToken(project, domain, domainOwner, region, localProfile)
        
        saveSSOTokenToCacheFile(project, tokenValue)
        return tokenValue
    }
    
    private boolean isRunByCircleCi() {
        return System.getenv("CIRCLECI") == "true"
    }
    
    private String readCachedSsoToken(Project project) {
        def file = new File(project.projectDir, SSO_CACHE_FILE)
        if (!file.exists()) {
            project.logger.debug("Local SSO cache does not exist")
            return null
        }
        
        def lines = file.readLines()
        if (lines.isEmpty()) {
            project.logger.debug("Local SSO cache is empty")
            return null
        }
        
        def lastLine = lines.last()
        if (lastLine.isBlank()) {
            project.logger.debug("Last line of local SSO cache is blank")
            return null
        }
        
        def tokens = lastLine.split(' ')
        try {
            def cachedTime = new java.text.SimpleDateFormat(TIMESTAMP_PATTERN).parse(tokens[0])
            def currentTime = new Date()
            
            use(TimeCategory) {
                if (currentTime.after(cachedTime + CACHE_EXPIRE_HOURS.hours)) {
                    project.logger.debug("Local SSO cache expires, timestamp = ${cachedTime.format(TIMESTAMP_PATTERN)}")
                    return null
                }
                
                return tokens[tokens.size() - 1]
            }
        } catch (Exception e) {
            project.logger.warn("Failed to parse cached SSO token: ${e.message}")
            return null
        }
    }
    
    private void saveSSOTokenToCacheFile(Project project, String token) {
        def currentTime = new Date().format(TIMESTAMP_PATTERN)
        project.logger.info("Updating local SSO cache with timestamp $currentTime")
        
        def file = new File(project.projectDir, SSO_CACHE_FILE)
        file.append("\n$currentTime $token")
    }
    
    private String loadLocalSsoToken(Project project, String domain, String domainOwner, String region, String localProfile) {
        project.logger.info("Loading SSO Token locally...")
        
        def process = [
            "/usr/local/bin/aws", "codeartifact", "get-authorization-token",
            "--domain", domain,
            "--domain-owner", domainOwner,
            "--query", "authorizationToken",
            "--output", "text",
            "--region", region,
            "--profile", localProfile
        ].execute()
        
        process.waitFor()
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to get AWS CodeArtifact token: ${process.errorStream.text}")
        }
        
        project.logger.info("Successfully loaded SSO Token locally")
        return process.text.trim()
    }
    
    private String loadCircleCiSsoToken(Project project, String domain, String domainOwner, String region) {
        project.logger.info("Loading SSO Token in CircleCI...")
        
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
            throw new RuntimeException("Failed to get AWS CodeArtifact token in CircleCI: ${process.errorStream.text}")
        }
        
        project.logger.info("Successfully loaded SSO Token in CircleCI")
        return process.text.trim()
    }
}
