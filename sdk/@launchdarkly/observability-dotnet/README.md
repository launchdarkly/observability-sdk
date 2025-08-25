LaunchDarkly Observability Plugin for .Net
===========================

([![Actions Status][dotnetplugin-sdk-ci-badge]][dotnetplugin-sdk-ci])
([![Documentation]&#40;https://img.shields.io/static/v1?label=GitHub+Pages&message=API+reference&color=00add8&#41;][o11y-docs-link])
([![NuGet][dotnetplugin-nuget-badge]][dotnetplugin-nuget-link])

# Early Access Preview️

**NB: APIs are subject to change until a 1.x version is released.**

## Install

```shell
dotnet add package LaunchDarkly.Observability
```

Install the plugin when configuring your LaunchDarkly SDK.

```csharp
 // TODO: Add example.
```

LaunchDarkly overview
-------------------------
[LaunchDarkly](https://www.launchdarkly.com) is a feature management platform that serves trillions of feature flags daily to help teams build better software, faster. [Get started](https://docs.launchdarkly.com/home/getting-started) using LaunchDarkly today!

[![Twitter Follow](https://img.shields.io/twitter/follow/launchdarkly.svg?style=social&label=Follow&maxAge=2592000)](https://twitter.com/intent/follow?screen_name=launchdarkly)

## Contributing

We encourage pull requests and other contributions from the community. Check out our [contributing guidelines](CONTRIBUTING.md) for instructions on how to contribute to this SDK.

## About LaunchDarkly

* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Check out [our documentation](https://docs.launchdarkly.com/docs) for a complete list.
* Explore LaunchDarkly
    * [launchdarkly.com](https://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com](https://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDK reference guides
    * [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [launchdarkly.com/blog](https://launchdarkly.com/blog/  "LaunchDarkly Blog Documentation") for the latest product updates

[dotnetplugin-sdk-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/dotnet-plugin.yml/badge.svg
[dotnetplugin-sdk-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/dotnet-plugin.yml
[o11y-docs-link]: https://launchdarkly.github.io/observability-sdk/sdk/@launchdarkly/observability-dotnet/
[dotnetplugin-nuget-badge]: https://img.shields.io/nuget/v/LaunchDarkly.Observability.svg?style=flat-square
[dotnetplugin-nuget-link]: https://www.nuget.org/packages/LaunchDarkly.Observability/