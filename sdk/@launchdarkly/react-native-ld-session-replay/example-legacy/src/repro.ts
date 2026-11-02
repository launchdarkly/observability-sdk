import {LDObserve} from '@launchdarkly/observability-react-native';

/**
 * Repro for customer request #1: app-wide attributes (e.g. UTM parameters)
 * on every span, log, error and metric.
 *
 * `applyGlobalAttributes()` runs right after `new ReactNativeLDClient(...)`,
 * while the observability SDK's async init is still in flight, so it also
 * covers attributes set before init. The span and log buttons on the API tab
 * print what to look for on the resulting rows.
 */
export const GLOBAL_ATTRIBUTES = {
  utm_campaign: 'spring_promo',
  utm_source: 'email',
};

export function applyGlobalAttributes() {
  LDObserve.setAttributes(GLOBAL_ATTRIBUTES);
}

export const globalAttributesNote = () =>
  `[#1] expect utm_campaign=${GLOBAL_ATTRIBUTES.utm_campaign} on this row`;

export const perCallOverrideNote = () =>
  '[#1] expect utm_source=per_call (per-call wins over global)';
