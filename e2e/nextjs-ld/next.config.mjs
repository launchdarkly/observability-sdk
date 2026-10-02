// next.config.mjs
import { withLaunchDarklyConfig } from '@launchdarkly/observability-next/config'

/** @type {import('next').NextConfig} */
const nextConfig = {}

export default withLaunchDarklyConfig(nextConfig)
