<p align="center">
  <img width="2051" alt="docs-thumbnail" src="https://user-images.githubusercontent.com/20292680/233754540-409ee4cf-beab-46b1-b313-d2d717a87fd6.png">
</p>
<p align="center">
  <a href='http://makeapullrequest.com'><img alt='PRs Welcome' src='https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=shields'/></a>
  <a href='https://highlight.io/community'><img alt="Join Discord Community" src="https://img.shields.io/badge/discord%20community-join-blue"/></a>
  <img alt="GitHub commit activity" src="https://img.shields.io/github/commit-activity/m/highlight/highlight"/>
  <img alt="GitHub closed issues" src="https://img.shields.io/github/issues-closed/highlight/highlight"/>
</p>

<p align="center">
  <a href="https://highlight.io/docs">Docs</a> - <a href="https://highlight.io/community">Community (Support & Feedback)</a> - <a href="https://github.com/highlight/highlight/issues/new?assignees=&labels=external+bug+%2F+request&template=feature_request.md&title=">Feature request</a> - <a href="https://github.com/highlight/highlight/issues/new?assignees=&labels=external+bug+%2F+request&template=bug_report.md&title=">Bug report</a>
</p>

# [highlight.io](https://highlight.io): The open-source, fullstack monitoring platform.

highlight.io is a monitoring tool for the next generation of developers (like you!). Unlike the age-old, outdated tools out there, we aim to build a [cohesive](#we-build-a-cohesive-product), [modern](#we-build-for-todays-developer) and [fully-featured](#features) monitoring solution, something we wished WE had. And it's all open source :)

At a high level, highlight.io's feature set is:
- [Session Replay](#session-replay-understand-why-bugs-happen)
- [Error Monitoring](#error-monitoring-understand-what-bugs-are-happening)
- [Logging](#logging)

We strive to make highlight.io as easy to install as a few lines of code in any environment.

Read more about our [features](#features), [values](#our-values) and [mission](#our-mission) below, and get started at https://highlight.io today!

# Highlight PHP SDK

Below are some examples demonstrating usage of the PHP SDK:
```php
    use Highlight\SDK\Common\HighlightOptions;
    use Highlight\SDK\Highlight;


    $projectId = '1jdkeo52';

    // Use only a projectId to bootstrap Highlight
    if (!Highlight::isInitialized()) {
        Highlight::init($projectId);
    }

    // Use a HighlightOptions instance to bootstrap Highlight
    $options = HighlightOptions::builder($projectId)->build();
    if (!Highlight::isInitialized()) {
        Highlight::initWithOptions($options);
    }

    // Use a HighlightOptions instance prepped with a serviceName to bootstrap Highlight
    $options = HighlightOptions::builder($projectId)->serviceName('test-service-01')->build();

    if (!Highlight::isInitialized()) {
        Highlight::initWithOptions($options);
    }

```
