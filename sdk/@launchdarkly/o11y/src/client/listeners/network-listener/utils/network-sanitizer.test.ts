import { describe, expect, it } from 'vitest'
import { redactLaunchDarklyContext, sanitizeUrl } from './network-sanitizer'

// base64url of a context JSON; not a real person.
const B64 =
	'eyJraW5kIjoidXNlciIsImtleSI6InUxIiwibmFtZSI6IkphbmUiLCJlbWFpbCI6ImphbmVAZXhhbXBsZS5jb20ifQ'

describe('redactLaunchDarklyContext', () => {
	it.each([
		[
			`https://clientstream.launchdarkly.com/eval/abc123/${B64}`,
			'https://clientstream.launchdarkly.com/eval/abc123/REDACTED',
		],
		[
			`https://clientstream.launchdarkly.com/eval/abc123/${B64}?h=deadbeef&withReasons=true`,
			'https://clientstream.launchdarkly.com/eval/abc123/REDACTED?h=deadbeef&withReasons=true',
		],
		[
			`https://clientsdk.launchdarkly.com/sdk/evalx/abc123/contexts/${B64}`,
			'https://clientsdk.launchdarkly.com/sdk/evalx/abc123/contexts/REDACTED',
		],
		[
			`https://clientsdk.launchdarkly.com/sdk/evalx/abc123/contexts/${B64}?h=deadbeef`,
			'https://clientsdk.launchdarkly.com/sdk/evalx/abc123/contexts/REDACTED?h=deadbeef',
		],
		[
			`https://clientsdk.launchdarkly.com/sdk/evalx/abc123/users/${B64}`,
			'https://clientsdk.launchdarkly.com/sdk/evalx/abc123/users/REDACTED',
		],
		[
			`https://stream.ld.example.com/eval/abc123/${B64}%3D%3D`,
			'https://stream.ld.example.com/eval/abc123/REDACTED',
		],
		// relative / path-only forms
		[`/eval/abc123/${B64}`, '/eval/abc123/REDACTED'],
		[
			`/sdk/evalx/abc123/contexts/${B64}`,
			'/sdk/evalx/abc123/contexts/REDACTED',
		],
	])('redacts the context segment in %s', (input, expected) => {
		expect(redactLaunchDarklyContext(input)).toBe(expected)
		expect(redactLaunchDarklyContext(input)).not.toContain(B64)
	})

	it('redacts the segment inside free text such as a log line', () => {
		const line = `[LaunchDarkly] Opening stream connection to https://clientstream.launchdarkly.com/eval/abc123/${B64}?h=x`
		expect(redactLaunchDarklyContext(line)).toBe(
			'[LaunchDarkly] Opening stream connection to https://clientstream.launchdarkly.com/eval/abc123/REDACTED?h=x',
		)
	})

	it('leaves everything else alone', () => {
		for (const url of [
			'https://api.example.com/eval/results',
			'https://clientsdk.launchdarkly.com/sdk/goals/abc123',
			'https://events.launchdarkly.com/events/bulk/abc123',
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
				`https://clientstream.launchdarkly.com/eval/abc123/${B64}?h=deadbeef&withReasons=true`,
			),
		).toBe(
			'https://clientstream.launchdarkly.com/eval/abc123/REDACTED?h=deadbeef&withReasons=true',
		)
	})

	it('redacts the context segment of a polling URL', () => {
		expect(
			sanitizeUrl(
				`https://clientsdk.launchdarkly.com/sdk/evalx/abc123/contexts/${B64}?h=deadbeef`,
			),
		).toBe(
			'https://clientsdk.launchdarkly.com/sdk/evalx/abc123/contexts/REDACTED?h=deadbeef',
		)
	})

	it('redacts the context segment of a relative URL', () => {
		expect(sanitizeUrl(`/sdk/evalx/abc123/users/${B64}`)).toBe(
			'/sdk/evalx/abc123/users/REDACTED',
		)
	})

	it('still redacts sensitive query parameters alongside it', () => {
		expect(
			sanitizeUrl(
				`https://host.example.com/eval/abc123/${B64}?token=s3cret`,
			),
		).toBe('https://host.example.com/eval/abc123/REDACTED?token=REDACTED')
	})
})
