# Plugin Verification Guide

This guide will help you verify the AWS CodeArtifact Gradle Plugin locally before publishing to the Gradle Plugin Portal.

## Step 1: Build the Plugin

First, build the plugin to generate the JAR file:

```bash
cd /Users/taoxiao/Code/github/tao-xiao-nj/aws-code-artifact-gradle-plugin
./gradlew :plugin:build
```

This will create the plugin JAR at `plugin/build/libs/plugin-0.1.0.jar`.

## Step 2: Verify Plugin Build

Check that the plugin was built successfully:

```bash
ls -la plugin/build/libs/
```

You should see:
- `plugin-0.1.0.jar`
- `plugin-0.1.0-sources.jar` 
- `plugin-0.1.0-javadoc.jar`

## Step 3: Test Plugin in Sample App

### Option A: Test with Mock Configuration (Recommended for Initial Testing)

The sample-app is configured to use the plugin locally using the direct class application method:

```bash
cd /Users/taoxiao/Code/github/tao-xiao-nj/aws-code-artifact-gradle-plugin
./gradlew :sample-app:help
```

This should:
- ✅ Apply the plugin successfully
- ⚠️ Show a warning about missing repository URL (expected)
- ✅ Complete the build without errors

**Note**: The plugin uses `apply plugin: org.example.AwsCodeArtifactGradlePluginPlugin` instead of the plugin ID for local testing.

### Option B: Test with Real AWS Configuration

If you have AWS CodeArtifact access, update the configuration in `sample-app/build.gradle`:

```gradle
awsCodeArtifact {
    repoUrl = 'https://your-real-domain-123456789012.d.codeartifact.us-west-2.amazonaws.com/maven/your-real-repo/'
    domain = 'your-real-domain'
    domainOwner = '123456789012'  // Your real AWS account ID
    region = 'us-west-2'
    localProfile = 'your-real-profile'
}
```

Then test:
```bash
cd sample-app
./gradlew help
```

## Step 4: Verify Plugin Functionality

### Check Plugin Extension
```bash
cd sample-app
./gradlew help --info | grep -i "awsCodeArtifact"
```

### Test Repository Configuration
```bash
cd sample-app
./gradlew dependencies --configuration compileClasspath
```

This will show if the CodeArtifact repository is properly configured.

### Test Token Caching
```bash
cd sample-app
./gradlew help
./gradlew help  # Second run should use cached token
```

Check for the cache file:
```bash
ls -la .ssoToken.records
```

## Step 5: Run Plugin Tests

Verify the plugin's own tests pass:

```bash
cd /Users/taoxiao/Code/github/tao-xiao-nj/aws-code-artifact-gradle-plugin
./gradlew :plugin:test
./gradlew :plugin:functionalTest
```

## Step 6: Test Different Scenarios

### Test with Missing Configuration
Temporarily comment out the `awsCodeArtifact` block in `sample-app/build.gradle` and run:

```bash
cd sample-app
./gradlew help
```

Should show a warning but not fail.

### Test with Minimal Configuration
Use only required fields:

```gradle
awsCodeArtifact {
    repoUrl = 'https://test-domain-123456789012.d.codeartifact.us-west-2.amazonaws.com/maven/test-repo/'
    domain = 'test-domain'
    domainOwner = '123456789012'
}
```

## Step 7: Verify Environment Detection

### Test Local Environment Detection
```bash
cd sample-app
./gradlew help --info | grep -i "loading SSO Token locally"
```

### Test CircleCI Environment Detection (Simulation)
```bash
cd sample-app
CIRCLECI=true ./gradlew help --info | grep -i "loading SSO Token in CircleCI"
```

## Expected Results

✅ **Plugin applies successfully**
✅ **Extension is created with correct defaults**
✅ **Repository is configured when URL is provided**
✅ **Warning is shown when URL is missing**
✅ **Token caching works (if AWS credentials are available)**
✅ **Environment detection works correctly**
✅ **All tests pass**
✅ **No configuration-time external process errors**
✅ **Lazy authentication works correctly**

## Troubleshooting

### Plugin Not Found
If you get "Plugin with id 'org.example.aws-codeartifact' not found":
1. Ensure you built the plugin: `./gradlew :plugin:build`
2. Check the JAR exists: `ls plugin/build/libs/plugin-0.1.0.jar`
3. Verify the buildscript dependency path is correct

### AWS Authentication Errors
If you get AWS authentication errors:
1. This is expected if you don't have real AWS credentials
2. The plugin should still apply and configure repositories
3. Errors will only occur when actually trying to resolve dependencies

### Build Failures
If the build fails:
1. Check the plugin's own tests: `./gradlew :plugin:test`
2. Verify the plugin JAR was built correctly
3. Check for any compilation errors in the plugin code

## Next Steps

Once verification is complete:
1. Update version numbers if needed
2. Commit your changes
3. Tag the release
4. Publish to Gradle Plugin Portal: `./gradlew :plugin:publishPlugins`
