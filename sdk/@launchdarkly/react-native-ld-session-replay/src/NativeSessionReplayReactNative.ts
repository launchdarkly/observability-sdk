import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type SessionReplayOptions = {
  isEnabled?: boolean;
  serviceName?: string;
  maskTextInputs?: boolean;
  maskWebViews?: boolean;
  maskLabels?: boolean;
  maskImages?: boolean;
  maskAccessibilityIdentifiers?: string[];
  unmaskAccessibilityIdentifiers?: string[];
  ignoreAccessibilityIdentifiers?: string[];
  minimumAlpha?: number;
};

export interface Spec extends TurboModule {
  configure(mobileKey: string, options?: Object): Promise<void>;
  startSessionReplay(): Promise<void>;
  stopSessionReplay(): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>(
  'SessionReplayReactNative'
);
