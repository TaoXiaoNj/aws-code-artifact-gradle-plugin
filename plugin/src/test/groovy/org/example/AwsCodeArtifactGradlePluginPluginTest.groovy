/*
 * Unit tests for the AWS CodeArtifact Gradle Plugin
 */
package org.example

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import spock.lang.Specification

/**
 * Unit tests for the AWS CodeArtifact plugin
 */
class AwsCodeArtifactGradlePluginPluginTest extends Specification {
    
    def "plugin can be instantiated"() {
        expect:
        new AwsCodeArtifactGradlePluginPlugin() != null
    }
    
    def "plugin applies successfully and creates extension"() {
        given:
        Project project = ProjectBuilder.builder().build()
        AwsCodeArtifactGradlePluginPlugin plugin = new AwsCodeArtifactGradlePluginPlugin()
        
        when:
        plugin.apply(project)
        
        then:
        project.extensions.findByType(AwsCodeArtifactExtension) != null
        project.extensions.findByName('awsCodeArtifact') != null
    }
    
    def "plugin sets default values for extension"() {
        given:
        Project project = ProjectBuilder.builder().build()
        AwsCodeArtifactGradlePluginPlugin plugin = new AwsCodeArtifactGradlePluginPlugin()
        
        when:
        plugin.apply(project)
        AwsCodeArtifactExtension extension = project.extensions.findByType(AwsCodeArtifactExtension)
        
        then:
        extension.localProfile == 'infra'
        extension.region == 'us-west-2'
        extension.cacheExpireHours == 4
    }
    
    def "plugin preserves custom values in extension"() {
        given:
        Project project = ProjectBuilder.builder().build()
        AwsCodeArtifactGradlePluginPlugin plugin = new AwsCodeArtifactGradlePluginPlugin()
        
        when:
        plugin.apply(project)
        AwsCodeArtifactExtension extension = project.extensions.findByType(AwsCodeArtifactExtension)
        extension.localProfile = 'custom-profile'
        extension.region = 'us-east-1'
        extension.cacheExpireHours = 8
        
        then:
        extension.localProfile == 'custom-profile'
        extension.region == 'us-east-1'
        extension.cacheExpireHours == 8
    }
}
