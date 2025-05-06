#!/bin/bash
cd /home/rlamb/code/launchdarkly/observability/observability-sdk
npx vitest run sdk/highlight-run/src/client/otel/sampling/CustomSampler.test.ts 