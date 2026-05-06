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
   * @deprecated Use `maskTestIDs` instead.
   */
  maskAccessibilityIdentifiers?: string[];

  /**
   * @deprecated Use `unmaskTestIDs` instead.
   */
  unmaskAccessibilityIdentifiers?: string[];

  ignoreAccessibilityIdentifiers?: string[];
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
