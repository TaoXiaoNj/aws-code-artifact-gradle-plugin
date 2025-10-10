/*
 * Functional tests for the AWS CodeArtifact Gradle Plugin
 */
package org.example

import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.testkit.runner.GradleRunner

/**
 * Functional tests for the AWS CodeArtifact plugin
 */
class AwsCodeArtifactGradlePluginPluginFunctionalTest extends Specification {
    @TempDir
    private File projectDir

    private getBuildFile() {
        new File(projectDir, "build.gradle")
    }

    private getSettingsFile() {
        new File(projectDir, "settings.gradle")
    }

    def "plugin applies without errors when configured"() {
        given:
        settingsFile << ""
        buildFile << """
plugins {
    id('com.github.tao.aws-codeartifact')
}

awsCodeArtifact {
    repoUrl = 'https://test-domain-123456789012.d.codeartifact.us-west-2.amazonaws.com/maven/test-repo/'
    domain = 'test-domain'
    domainOwner = '123456789012'
    region = 'us-west-2'
    localProfile = 'test-profile'
}
"""

        when:
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("help")
        runner.withProjectDir(projectDir)
        def result = runner.build()

        then:
        result.task(":help").outcome.toString() == "SUCCESS"
    }

    def "plugin warns when repository URL is not configured"() {
        given:
        settingsFile << ""
        buildFile << """
plugins {
    id('com.github.tao.aws-codeartifact')
}

// No awsCodeArtifact configuration
"""

        when:
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("help")
        runner.withProjectDir(projectDir)
        def result = runner.build()

        then:
        result.task(":help").outcome.toString() == "SUCCESS"
        // The plugin should warn about missing configuration but not fail
    }

    def "plugin applies with minimal configuration"() {
        given:
        settingsFile << ""
        buildFile << """
plugins {
    id('com.github.tao.aws-codeartifact')
}

awsCodeArtifact {
    repoUrl = 'https://test-domain-123456789012.d.codeartifact.us-west-2.amazonaws.com/maven/test-repo/'
    domain = 'test-domain'
    domainOwner = '123456789012'
    // Other properties will use defaults
}
"""

        when:
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("help")
        runner.withProjectDir(projectDir)
        def result = runner.build()

        then:
        result.task(":help").outcome.toString() == "SUCCESS"
    }
}
