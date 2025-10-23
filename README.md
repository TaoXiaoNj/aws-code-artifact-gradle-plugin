# AWS CodeArtifact Gradle Plugin

A Gradle plugin that simplifies AWS CodeArtifact repository configuration with automatic SSO token authentication and caching.

## Features

- **Automatic Authentication**: Automatically handles AWS CodeArtifact SSO token authentication
- **Token Caching**: Caches SSO tokens locally to avoid repeated authentication requests
- **Environment Detection**: Automatically detects if running in CircleCI or locally
- **Easy Configuration**: Simple configuration through Gradle extension
- **Cross-Platform**: Works on both local development and CI/CD environments

## Pre-requisites

1. [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) is installed.
2. A named AWS [profile](https://docs.aws.amazon.com/cli/v1/userguide/cli-configure-files.html) is configured.
3. JDK 18+ is installed. 


## Usage

### Applying the plugin

Add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'io.github.taoxiaonj.aws-codeartifact' version '0.2.4'
}
```

### Configuration

Configure your AWS CodeArtifact repository:

```gradle
awsCodeArtifact {
    repoUrl = 'https://{domain}-{account}.d.codeartifact.{region}.amazonaws.com/{your-repo}'
    localProfile = 'your-aws-profile'
}
```

### Required Configuration Properties
- `repoUrl`: The full URL of your AWS CodeArtifact repository
- `localProfile`: AWS profile name for local development



## Case of running in CI/CD (CircleCI)

The plugin automatically detects CircleCI environment and uses the appropriate authentication method. Ensure your CI environment has:

1. AWS credentials configured
2. Proper IAM permissions for CodeArtifact

## Case of running locally

If you have not logged in using AWS profile, or the earlier login has expired: 
- you will be prompted to log-in again in your default browser.
- an authentication web page will be automatically opened then you can finish the login step

When log-in succeeds, the plugin will automatically fetch and cache the token, so that you don't need to fetch it again next time.

## How It Works

1. **Token Caching**: The plugin caches SSO tokens in a local `~/.gradle/awsCodeArtifact/ssoToken.records` file
2. **Automatic Refresh**: Tokens are automatically refreshed when they expire
3. **Repository Configuration**: Automatically configures the Maven repository with credentials
4. **Environment Detection**: Uses different authentication methods for local vs CI environments

## Example

```gradle
plugins {
    id 'java'
    id 'io.github.taoxiaonj.aws-codeartifact' version '0.2.4'
}

awsCodeArtifact {
    repoUrl = 'https://mycompany-123456789012.d.codeartifact.us-west-2.amazonaws.com/maven/maven-central/'
    localProfile = 'mycompany-dev'
}

dependencies {
    implementation 'com.mycompany:my-library:1.0.0'
}
```

## Building and Publishing

To build the plugin:

```bash
./gradlew :plugin:clean :plugin:build  --no-configuration-cache
```

To publish to Gradle Plugin Portal:

```bash
./gradlew publishPlugins
```

## Tuning

To avoid distracting you the logs are by default disabled. 

To see the logs, you can run the build command with an option `--info`.

For example: 

```shell
./gradlew :plugin:build --info
```

You would see log messages like:

```aiignore
   >>> 🚀  Applying plugin awsCodeArtifact ...
   >>> Parsing repoUrl succeeded: domain = 'aa-bb', account = '12345', region = 'us-west-2'
   >>> Start loading SSO token ...
   >>> Local SSO cache does not exist
   >>> Retrieving new SSO token ...
   >>> Fetching SSO token with profile 'infra' ...
   >>> ✅ Successfully fetched SSO token
   >>> Caching SSO cache with timestamp 20251015-154008
```


## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License.
