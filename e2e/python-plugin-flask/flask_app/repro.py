"""Repro #1: global attributes (e.g. UTM params) on all telemetry."""

from ldobserve import observe

GLOBAL_ATTRIBUTES = {"utm_campaign": "spring_promo", "utm_source": "email"}

EXPECT_GLOBAL = "[#1] expect utm_campaign=spring_promo on this row"
EXPECT_PER_CALL = "[#1] expect utm_source=per_call (per-call wins over global)"


def apply_global_attributes():
    # Called before the LaunchDarkly client and observability plugin are
    # initialized, to show that global attributes can be set before init.
    observe.set_attributes(GLOBAL_ATTRIBUTES)


def expect(response, *expectations):
    """Print the expectations and append them to the route's response."""
    for expectation in expectations:
        print(expectation)
    return "\n".join((response, *expectations))
