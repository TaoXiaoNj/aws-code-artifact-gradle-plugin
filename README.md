# AWS CodeArtifact Gradle Plugin

A Gradle plugin that simplifies AWS CodeArtifact repository configuration with automatic SSO token authentication and caching.

## Features

- **Automatic Authentication**: Automatically handles AWS CodeArtifact SSO token authentication
- **Token Caching**: Caches SSO tokens locally to avoid repeated authentication requests
- **Environment Detection**: Automatically detects if running in CircleCI or locally
- **Easy Configuration**: Simple configuration through Gradle extension
- **Cross-Platform**: Works on both local development and CI/CD environments

## Usage

### Applying the plugin

Add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'io.github.taoxiaonj.aws-codeartifact' version '0.1.0'
}
```

### Configuration

Configure your AWS CodeArtifact repository:

```gradle
awsCodeArtifact {
    repoUrl = 'https://your-domain-123456789012.d.codeartifact.us-west-2.amazonaws.com/maven/your-repo/'
    domain = 'your-domain'
    domainOwner = '123456789012'
    region = 'us-west-2'
    localProfile = 'your-aws-profile'
    cacheExpireHours = 4
}
```

### Required Configuration Properties

- `repoUrl`: The full URL of your AWS CodeArtifact repository
- `domain`: Your AWS CodeArtifact domain name
- `domainOwner`: Your AWS account ID (12 digits)

### Optional Configuration Properties

- `region`: AWS region
- `localProfile`: AWS profile name for local development
- `cacheExpireHours`: SSO token cache expiration time in hours

## Prerequisites

### Local Development

1. **AWS CLI**: Install and configure AWS CLI
2. **AWS Profile**: Set up an AWS profile with CodeArtifact permissions
3. **SSO Login**: Ensure you're logged in to AWS SSO:
   ```bash
   aws sso login --profile {local-profile-name}
   ```

### CI/CD (CircleCI)

The plugin automatically detects CircleCI environment and uses the appropriate authentication method. Ensure your CI environment has:

1. AWS credentials configured
2. Proper IAM permissions for CodeArtifact

## How It Works

1. **Token Caching**: The plugin caches SSO tokens in a local `.ssoToken.records` file
2. **Automatic Refresh**: Tokens are automatically refreshed when they expire
3. **Repository Configuration**: Automatically configures the Maven repository with credentials
4. **Environment Detection**: Uses different authentication methods for local vs CI environments

## Example

```gradle
plugins {
    id 'java'
    id 'io.github.taoxiaonj.aws-codeartifact' version '0.1.0'
}

awsCodeArtifact {
    repoUrl = 'https://mycompany-123456789012.d.codeartifact.us-west-2.amazonaws.com/maven/maven-central/'
    domain = 'mycompany'
    domainOwner = '123456789012'
    region = 'us-west-2'
    localProfile = 'mycompany-dev'
}

dependencies {
    implementation 'com.mycompany:my-library:1.0.0'
}
```

## Building and Publishing

To build the plugin:

```bash
./gradlew build
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
./gradlew build --info
```

You would see log messages like:

```aiignore
Resolved plugin [id: 'java']
   >>> Applying plugin awsCodeArtifact ...
   >>> Start loading SSO token ...
   >>> Local SSO cache does not exist
   >>> Retrieving new SSO token ...
   >>> Fetching SSO token with profile 'infra' ...
   >>> ✅ Successfully fetched SSO token
   >>> Caching SSO cache with timestamp 20251015-152931
```


## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License.
