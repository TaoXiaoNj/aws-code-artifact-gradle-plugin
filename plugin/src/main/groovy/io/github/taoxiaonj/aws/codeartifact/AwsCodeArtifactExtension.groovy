/*
 * AWS CodeArtifact Extension
 * Configuration extension for the AWS CodeArtifact Gradle Plugin
 */
package io.github.taoxiaonj.aws.codeartifact

import org.gradle.api.Project

/**
 * Extension class for AWS CodeArtifact plugin configuration
 */
class AwsCodeArtifactExtension {
    /**
     * AWS CodeArtifact repository URL
     */
    String repoUrl
    
    /**
     * Local AWS profile name for authentication
     */
    String localProfile
    
    /**
     * SSO token cache expiration time in hours
     */
    Integer cacheExpireHours
    
    AwsCodeArtifactExtension(Project project) {
        // Default values will be set in the plugin class
    }
}
