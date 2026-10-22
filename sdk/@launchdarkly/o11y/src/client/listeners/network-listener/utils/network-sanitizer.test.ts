import { describe, expect, it } from 'vitest'
import { redactLaunchDarklyContext, sanitizeUrl } from './network-sanitizer'

// base64url of a context JSON; not a real person.
// A client-side ID is a 24-character hex string.
const ENV = '6ac3ffaf1d5ff3065c65987b'
const B64 =
	'eyJraW5kIjoidXNlciIsImtleSI6InUxIiwibmFtZSI6IkphbmUiLCJlbWFpbCI6ImphbmVAZXhhbXBsZS5jb20ifQ'

describe('redactLaunchDarklyContext', () => {
	it.each([
		[
			`https://clientstream.launchdarkly.com/eval/${ENV}/${B64}`,
			`https://clientstream.launchdarkly.com/eval/${ENV}/REDACTED`,
		],
		[
			`https://clientstream.launchdarkly.com/eval/${ENV}/${B64}?h=deadbeef&withReasons=true`,
			`https://clientstream.launchdarkly.com/eval/${ENV}/REDACTED?h=deadbeef&withReasons=true`,
		],
		[
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/contexts/${B64}`,
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/contexts/REDACTED`,
		],
		[
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/contexts/${B64}?h=deadbeef`,
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/contexts/REDACTED?h=deadbeef`,
		],
		[
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/users/${B64}`,
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/users/REDACTED`,
		],
		[
			`https://stream.ld.example.com/eval/${ENV}/${B64}%3D%3D`,
			`https://stream.ld.example.com/eval/${ENV}/REDACTED`,
		],
		// relative / path-only forms
		[`/eval/${ENV}/${B64}`, `/eval/${ENV}/REDACTED`],
		[
			`/sdk/evalx/${ENV}/contexts/${B64}`,
			`/sdk/evalx/${ENV}/contexts/REDACTED`,
		],
	])('redacts the context segment in %s', (input, expected) => {
		expect(redactLaunchDarklyContext(input)).toBe(expected)
		expect(redactLaunchDarklyContext(input)).not.toContain(B64)
	})

	it('redacts the segment inside free text such as a log line', () => {
		const line = `[LaunchDarkly] Opening stream connection to https://clientstream.launchdarkly.com/eval/${ENV}/${B64}?h=x`
		expect(redactLaunchDarklyContext(line)).toBe(
			`[LaunchDarkly] Opening stream connection to https://clientstream.launchdarkly.com/eval/${ENV}/REDACTED?h=x`,
		)
	})

	it('leaves everything else alone', () => {
		for (const url of [
			'https://api.example.com/eval/results',
			'https://api.example.com/eval/batch/42',
			'https://api.example.com/evalx/batch/42/items',
			// not an LD client-side ID in the environment slot
			`https://clientstream.launchdarkly.com/eval/abc123/${B64}`,
			// the polling form without its contexts/users segment
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/${B64}`,
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/goals`,
			`https://clientsdk.launchdarkly.com/sdk/goals/${ENV}`,
			`https://events.launchdarkly.com/events/bulk/${ENV}`,
			'https://example.com/evaluate/abc/def',
			'plain text with no URL',
			'',
		]) {
			expect(redactLaunchDarklyContext(url)).toBe(url)
		}
	})
})

describe('sanitizeUrl and LaunchDarkly SDK endpoints', () => {
	it('redacts the context segment of a streaming URL', () => {
		expect(
			sanitizeUrl(
				`https://clientstream.launchdarkly.com/eval/${ENV}/${B64}?h=deadbeef&withReasons=true`,
			),
		).toBe(
			`https://clientstream.launchdarkly.com/eval/${ENV}/REDACTED?h=deadbeef&withReasons=true`,
		)
	})

	it('redacts the context segment of a polling URL', () => {
		expect(
			sanitizeUrl(
				`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/contexts/${B64}?h=deadbeef`,
			),
		).toBe(
			`https://clientsdk.launchdarkly.com/sdk/evalx/${ENV}/contexts/REDACTED?h=deadbeef`,
		)
	})

	it('redacts the context segment of a relative URL', () => {
		expect(sanitizeUrl(`/sdk/evalx/${ENV}/users/${B64}`)).toBe(
			`/sdk/evalx/${ENV}/users/REDACTED`,
		)
	})

	it('still redacts sensitive query parameters alongside it', () => {
		expect(
			sanitizeUrl(
				`https://host.example.com/eval/${ENV}/${B64}?token=s3cret`,
			),
		).toBe(`https://host.example.com/eval/${ENV}/REDACTED?token=REDACTED`)
	})
})
