import 'package:flutter/foundation.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

// Repro #1: global attributes (e.g. UTM params) on all telemetry.
//
// [applyGlobalAttributes] runs at startup right after `LDObserve.init` is
// called, before observability has finished booting, so these attributes are
// set before init. The "Send custom span" and "Trigger Log" buttons print what
// to look for on the resulting rows in LaunchDarkly.

const globalAttributes = <String, Object?>{
  'utm_campaign': 'spring_promo',
  'utm_source': 'email',
};

void applyGlobalAttributes() => LDObserve.setAttributes(globalAttributes);

void printSpanExpectation() =>
    debugPrint('[#1] expect utm_campaign=spring_promo on this row');

void printLogExpectation() {
  debugPrint('[#1] expect utm_campaign=spring_promo on this row');
  debugPrint('[#1] expect utm_source=per_call (per-call wins over global)');
}
