package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.integrations.SdkMetadata;
import com.launchdarkly.sdk.client.subsystems.ApplicationInfo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

import java.util.Locale;
import java.util.Properties;

/**
 * Builds the resource every signal carries. The attributes, and the order in which later ones win,
 * follow the Android plugin's {@code buildObservabilityResource}:
 * <ol>
 *   <li>OpenTelemetry's default resource attributes, without {@code service.name};</li>
 *   <li>{@code highlight.project_id}, the credential, which the backend files telemetry under;</li>
 *   <li>{@code telemetry.distro.name} and {@code telemetry.distro.version};</li>
 *   <li>the operating system and Java runtime, which on Android the RUM library adds;</li>
 *   <li>{@link ObservabilityOptions#getResourceAttributes()};</li>
 *   <li>{@code service.name} and {@code service.version};</li>
 *   <li>{@code launchdarkly.application.id}, {@code launchdarkly.application.version} and
 *   {@code launchdarkly.sdk.version}, from the SDK, when there is an {@code LDClient}.</li>
 * </ol>
 */
final class ObservabilityResource {
  private ObservabilityResource() {}

  static Resource build(String credential, ObservabilityOptions options, EnvironmentMetadata metadata) {
    return build(credential, options, metadata, System.getProperties());
  }

  static Resource build(String credential, ObservabilityOptions options, EnvironmentMetadata metadata,
      Properties system) {
    AttributesBuilder builder = Resource.getDefault().getAttributes().toBuilder();
    builder.removeIf(key -> key.getKey().equals("service.name"));
    builder.put(Constants.PROJECT_ID_ATTRIBUTE, credential);
    builder.put("telemetry.distro.name", Constants.DISTRO_NAME);
    builder.put("telemetry.distro.version", Version.VERSION);
    putHostAttributes(builder, system);
    builder.putAll(options.getResourceAttributes());
    builder.put("service.name", options.getServiceName());
    builder.put("service.version", options.getServiceVersion() != null ? options.getServiceVersion() : Version.VERSION);

    ApplicationInfo application = metadata == null ? null : metadata.getApplicationInfo();
    if (application != null) {
      putIfPresent(builder, "launchdarkly.application.id", application.getApplicationId());
      putIfPresent(builder, "launchdarkly.application.version", application.getApplicationVersion());
    }
    SdkMetadata sdk = metadata == null ? null : metadata.getSdkMetadata();
    if (sdk != null && sdk.getName() != null && sdk.getVersion() != null) {
      builder.put("launchdarkly.sdk.version", sdk.getName() + "/" + sdk.getVersion());
    }
    return Resource.create(builder.build());
  }

  /** OpenTelemetry's semantic-convention names for the machine and runtime. */
  private static void putHostAttributes(AttributesBuilder builder, Properties system) {
    String osName = system.getProperty("os.name");
    String osVersion = system.getProperty("os.version");
    putIfPresent(builder, "os.type", osType(osName));
    putIfPresent(builder, "os.version", osVersion);
    if (osName != null) {
      builder.put("os.description", osVersion == null ? osName : osName + " " + osVersion);
    }
    putIfPresent(builder, "host.arch", hostArch(system.getProperty("os.arch")));
    putIfPresent(builder, "process.runtime.name", system.getProperty("java.runtime.name"));
    putIfPresent(builder, "process.runtime.version", system.getProperty("java.runtime.version"));
    String vendor = system.getProperty("java.vm.vendor");
    String vm = system.getProperty("java.vm.name");
    if (vendor != null && vm != null) {
      builder.put("process.runtime.description", vendor + " " + vm + " " + system.getProperty("java.vm.version", ""));
    }
  }

  /** The {@code os.type} value for an {@code os.name}, or null for a system it has no value for. */
  static String osType(String osName) {
    if (osName == null) {
      return null;
    }
    String name = osName.toLowerCase(Locale.ROOT);
    if (name.startsWith("windows")) {
      return "windows";
    }
    if (name.startsWith("mac") || name.startsWith("darwin")) {
      return "darwin";
    }
    if (name.startsWith("linux")) {
      return "linux";
    }
    if (name.startsWith("freebsd")) {
      return "freebsd";
    }
    if (name.startsWith("sunos") || name.startsWith("solaris")) {
      return "solaris";
    }
    if (name.startsWith("aix")) {
      return "aix";
    }
    return null;
  }

  /** The {@code host.arch} value for an {@code os.arch}, or null for an architecture it has no value for. */
  static String hostArch(String osArch) {
    if (osArch == null) {
      return null;
    }
    switch (osArch.toLowerCase(Locale.ROOT)) {
      case "amd64":
      case "x86_64":
        return "amd64";
      case "aarch64":
      case "arm64":
        return "arm64";
      case "x86":
      case "i386":
      case "i686":
        return "x86";
      case "arm":
        return "arm32";
      case "ppc64":
      case "ppc64le":
        return "ppc64";
      case "s390x":
        return "s390x";
      default:
        return null;
    }
  }

  private static void putIfPresent(AttributesBuilder builder, String key, String value) {
    if (value != null && !value.isEmpty()) {
      builder.put(AttributeKey.stringKey(key), value);
    }
  }

  /** For tests: the attributes alone. */
  static Attributes attributes(String credential, ObservabilityOptions options, EnvironmentMetadata metadata,
      Properties system) {
    return build(credential, options, metadata, system).getAttributes();
  }
}
