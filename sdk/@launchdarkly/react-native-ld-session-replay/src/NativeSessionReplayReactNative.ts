import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type SessionReplayOptions = {
  isEnabled?: boolean;
  serviceName?: string;
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
