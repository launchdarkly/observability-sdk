import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type SessionReplayOptions = {
  isEnabled?: boolean;
  /**
   * The OpenTelemetry `service.name` reported by the native session replay /
   * observability instance. Applied to both the observability and session replay
   * plugins on iOS and Android.
   */
  serviceName?: string;
  /**
   * The OpenTelemetry `service.version` reported by the native observability
   * instance. Applied to the observability plugin on iOS and Android. The native
   * session replay plugin does not expose a version, so this only affects
   * observability-emitted signals.
   */
  serviceVersion?: string;
  maskTextInputs?: boolean;
  maskWebViews?: boolean;
  maskLabels?: boolean;
  maskImages?: boolean;

  /**
   * Mask views whose `testID` prop is in this list. Match is exact string equality.
   */
  maskTestIDs?: string[];

  /**
   * Override masking for views whose `testID` prop is in this list. Takes precedence
   * over global masking rules. Match is exact string equality.
   */
  unmaskTestIDs?: string[];

  /**
   * iOS only. Mask views whose effective opacity is below this threshold (0.0–1.0).
   * Defaults to `0.02`. Has no effect on Android.
   */
  minimumAlpha?: number;

  /**
   * Session id to adopt for the native session replay / observability instance,
   * so its spans (e.g. `click`) share the same `session.id` as the JS
   * observability SDK. When provided, the native side seeds its observability
   * session with this id so both pipelines report a single session.
   *
   * Supported on both platforms: iOS starts observability with
   * `start(sessionId:)`, and Android forwards it as the `Observability` plugin's
   * `customSessionId` (seeding its session manager).
   */
  sessionId?: string;
};

export interface Spec extends TurboModule {
  configure(mobileKey: string, options?: Object): Promise<void>;
  startSessionReplay(): Promise<void>;
  stopSessionReplay(): Promise<void>;
  afterIdentify(
    contextKeys: Object,
    canonicalKey: string,
    completed: boolean
  ): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>(
  'SessionReplayReactNative'
);
