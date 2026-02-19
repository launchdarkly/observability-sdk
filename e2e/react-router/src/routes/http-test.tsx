import { useEffect, useState } from 'react'
import { recordObservability } from '../ldclientLazy'

interface TestButtonProps {
	title: string
	description: string
	onClick: () => Promise<void> | void
}

function TestButton({ title, description, onClick }: TestButtonProps) {
	return (
		<button
			onClick={onClick}
			style={{
				padding: '12px',
				textAlign: 'left',
				border: '1px solid #ccc',
				borderRadius: '4px',
				cursor: 'pointer',
			}}
		>
			<strong>{title}</strong>
			<br />
			<small>{description}</small>
		</button>
	)
}

interface TestSectionProps {
	title: string
	description: string
	children: React.ReactNode
}

function TestSection({ title, description, children }: TestSectionProps) {
	return (
		<section style={{ marginTop: '2rem' }}>
			<h2>{title}</h2>
			<p>{description}</p>
			<div
				style={{
					display: 'grid',
					gridTemplateColumns:
						'repeat(auto-fill, minmax(250px, 1fr))',
					gap: '12px',
					marginTop: '1rem',
				}}
			>
				{children}
			</div>
		</section>
	)
}

export default function HttpTest() {
	const [isRecording, setIsRecording] = useState(false)

	useEffect(() => {
		// Auto-start observability recording when page loads
		const startRecording = async () => {
			await recordObservability()
			setIsRecording(true)
			console.log('Observability recording started automatically')
		}
		startRecording()
	}, [])

	return (
		<div style={{ padding: '2rem', maxWidth: '1200px' }}>
			<div style={{ marginBottom: '1rem' }}>
				<a
					href="/"
					style={{
						display: 'inline-block',
						padding: '8px 16px',
						backgroundColor: '#f0f0f0',
						border: '1px solid #ccc',
						borderRadius: '4px',
						textDecoration: 'none',
						color: '#333',
					}}
				>
					← Back to Main
				</a>
			</div>
			<h1>HTTP Request Testing</h1>
			<p style={{ color: isRecording ? 'green' : 'orange' }}>
				{isRecording
					? '✓ Observability recording active'
					: '⏳ Starting recording...'}
			</p>

			<TestSection
				title="Header Sanitization Tests"
				description="These requests test that sensitive headers are properly redacted in the observability data."
			>
				<TestButton
					title="Authorization Header"
					description="Should be [REDACTED]"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts/1?test=authorization-header',
								{
									headers: {
										Authorization:
											'Bearer super-secret-token-12345',
									},
								},
							)
							const data = await response.json()
							console.log('Authorization header response:', data)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>

				<TestButton
					title="Cookie Header"
					description="Should be [REDACTED]"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/2?test=cookie-header',
							{
								headers: {
									Cookie: 'sessionid=abc123; user_token=xyz789; preferences=dark_mode',
								},
							},
						)
						const data = await response.json()
						console.log('Cookie header response:', data)
					}}
				/>

				<TestButton
					title="Proxy-Authorization"
					description="Should be [REDACTED]"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/3?test=proxy-auth-header',
							{
								headers: {
									'Proxy-Authorization':
										'Basic dXNlcjpwYXNzd29yZA==',
								},
							},
						)
						const data = await response.json()
						console.log('Proxy-Authorization response:', data)
					}}
				/>

				<TestButton
					title="Token Header"
					description="Should be [REDACTED]"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/4?test=token-header',
							{
								headers: {
									Token: 'my-custom-token-value',
								},
							},
						)
						const data = await response.json()
						console.log('Token header response:', data)
					}}
				/>

				<TestButton
					title="Safe Headers"
					description="Should NOT be redacted"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/5?test=safe-headers',
							{
								headers: {
									'Content-Type': 'application/json',
									'X-Custom-Header': 'safe-value',
									'User-Agent': 'TestApp/1.0',
								},
							},
						)
						const data = await response.json()
						console.log('Safe headers response:', data)
					}}
				/>

				<TestButton
					title="Mixed Headers"
					description="Auth/Cookie redacted, others visible"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/6?test=mixed-headers',
							{
								headers: {
									Authorization: 'Bearer should-be-redacted',
									Cookie: 'session=should-be-redacted',
									'Content-Type': 'application/json',
									'X-Request-ID': '12345',
								},
							},
						)
						const data = await response.json()
						console.log('Mixed headers response:', data)
					}}
				/>
			</TestSection>

			<TestSection
				title="URL Sanitization Tests"
				description="These requests test that sensitive data in URLs (credentials, query params) are properly sanitized."
			>
				<TestButton
					title="URL with Credentials"
					description="user:password → REDACTED:REDACTED"
					onClick={async () => {
						// Note: This will fail due to invalid credentials, but we're testing URL sanitization
						try {
							await fetch(
								'https://user:password@jsonplaceholder.typicode.com/posts/10?test=url-with-credentials',
							)
						} catch (e) {
							console.log('Expected error:', e)
						}
					}}
				/>

				<TestButton
					title="Sensitive Query Param (sig)"
					description="sig=secret123 → sig=REDACTED"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/11?test=sig-param&sig=secret123',
						)
						const data = await response.json()
						console.log('Query param (sig) response:', data)
					}}
				/>

				<TestButton
					title="AWS Access Key in URL"
					description="awsAccessKeyId → REDACTED"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/12?test=aws-key&awsAccessKeyId=AKIAIOSFODNN7EXAMPLE',
						)
						const data = await response.json()
						console.log('AWS access key response:', data)
					}}
				/>

				<TestButton
					title="Multiple Signatures"
					description="Both signature params → REDACTED"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/13?test=multiple-sigs&signature=abc123&x-goog-signature=xyz789',
						)
						const data = await response.json()
						console.log('Multiple signatures response:', data)
					}}
				/>

				<TestButton
					title="Safe Query Params"
					description="Should NOT be redacted"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts?userId=1&test=safe-params&_limit=5',
						)
						const data = await response.json()
						console.log('Safe query params response:', data)
					}}
				/>
			</TestSection>

			<TestSection
				title="Request Body Tests"
				description="These test that request/response bodies are properly recorded or redacted."
			>
				<TestButton
					title="POST with JSON Body"
					description="Body should be recorded"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts?test=post-json-body',
								{
									method: 'POST',
									headers: {
										'Content-Type': 'application/json',
									},
									body: JSON.stringify({
										title: 'Test Post',
										body: 'This is a test post body',
										userId: 1,
									}),
								},
							)
							const data = await response.json()
							console.log('POST response:', data)
						} catch (e) {
							console.error('POST request error:', e)
						}
					}}
				/>

				<TestButton
					title="GraphQL Request"
					description="Operation name should be recorded"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://api.github.com/graphql?test=graphql-request',
								{
									method: 'POST',
									headers: {
										'Content-Type': 'application/json',
									},
									body: JSON.stringify({
										operationName: 'GetViewer',
										query: 'query GetViewer { viewer { login name } }',
										variables: { id: '123' },
									}),
								},
							)
							const data = await response.json()
							console.log('GraphQL response:', data)
						} catch (e) {
							console.error('GraphQL request error:', e)
						}
					}}
				/>

				<TestButton
					title="PUT with Auth Header"
					description="Body visible, auth redacted"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts/20?test=put-with-auth',
								{
									method: 'PUT',
									headers: {
										'Content-Type': 'application/json',
										Authorization: 'Bearer secret-token',
									},
									body: JSON.stringify({
										id: 20,
										title: 'Updated title',
										body: 'Updated body',
										userId: 1,
									}),
								},
							)
							const data = await response.json()
							console.log('PUT response:', data)
						} catch (e) {
							console.error('PUT request error:', e)
						}
					}}
				/>

				<TestButton
					title="PATCH Request"
					description="Partial update with body"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts/21?test=patch-request',
								{
									method: 'PATCH',
									headers: {
										'Content-Type': 'application/json',
									},
									body: JSON.stringify({
										title: 'Patched title',
									}),
								},
							)
							const data = await response.json()
							console.log('PATCH response:', data)
						} catch (e) {
							console.error('PATCH request error:', e)
						}
					}}
				/>
			</TestSection>

			<TestSection
				title="Header Attributes Tests"
				description="Test that headers are properly recorded as span attributes (arrays vs strings)."
			>
				<TestButton
					title="Single-Value Headers"
					description="Headers with single values (strings)"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/60?test=single-value-headers',
							{
								headers: {
									'Content-Type': 'application/json',
									'X-Request-ID': 'req-12345',
									'X-Api-Version': 'v1',
									'User-Agent': 'TestApp/1.0',
								},
							},
						)
						const data = await response.json()
						console.log('Single-value headers response:', data)
					}}
				/>

				<TestButton
					title="Multi-Value Headers (Accept)"
					description="Accept header with multiple values"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/61?test=multi-value-accept',
							{
								headers: {
									Accept: 'application/json, text/plain, */*',
								},
							},
						)
						const data = await response.json()
						console.log('Multi-value Accept response:', data)
					}}
				/>

				<TestButton
					title="Multi-Value Cache-Control"
					description="Cache-Control with multiple directives"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/62?test=multi-value-cache',
							{
								headers: {
									'Cache-Control':
										'no-cache, no-store, must-revalidate',
								},
							},
						)
						const data = await response.json()
						console.log('Multi-value Cache-Control response:', data)
					}}
				/>

				<TestButton
					title="Multiple Custom Headers"
					description="Mix of single and multi-value headers"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/63?test=mixed-header-values',
							{
								headers: {
									'Content-Type': 'application/json',
									Accept: 'application/json, application/xml, text/html',
									'X-Custom-Single': 'single-value',
									'X-Custom-Multi': 'value1, value2, value3',
									'Accept-Language':
										'en-US, en;q=0.9, es;q=0.8',
								},
							},
						)
						const data = await response.json()
						console.log('Mixed header values response:', data)
					}}
				/>

				<TestButton
					title="Response Headers Check"
					description="Verify response headers are captured"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts/64?test=response-headers-check',
						)
						console.log('Response headers:')
						response.headers.forEach((value, key) => {
							console.log(`  ${key}: ${value}`)
						})
						const data = await response.json()
						console.log('Response data:', data)
					}}
				/>

				<TestButton
					title="POST with Request/Response Headers"
					description="Both request and response headers"
					onClick={async () => {
						const response = await fetch(
							'https://jsonplaceholder.typicode.com/posts?test=post-with-headers',
							{
								method: 'POST',
								headers: {
									'Content-Type': 'application/json',
									Accept: 'application/json, text/plain',
									'X-Request-ID': 'post-req-789',
									'X-Client-Version': '2.0.0',
								},
								body: JSON.stringify({
									title: 'Header Test Post',
									body: 'Testing header attributes',
									userId: 1,
								}),
							},
						)
						console.log('POST response headers:')
						response.headers.forEach((value, key) => {
							console.log(`  ${key}: ${value}`)
						})
						const data = await response.json()
						console.log('POST response data:', data)
					}}
				/>
			</TestSection>

			<TestSection
				title="Response Body Capture Tests"
				description="Test that http.response.body is captured on fetch spans. Previously, the async body read raced with span.end() causing the body attribute to be silently dropped."
			>
				<TestButton
					title="GET Response Body"
					description="http.response.body should contain JSON"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts/1?test=response-body-get',
							)
							const data = await response.json()
							console.log('GET response body capture test:', data)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>

				<TestButton
					title="Large Response Body"
					description="Body capture with ~5KB response"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts?test=response-body-large&_limit=10',
							)
							const data = await response.json()
							console.log(
								'Large response body capture test:',
								`${data.length} items, ~${JSON.stringify(data).length} bytes`,
							)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>

				<TestButton
					title="POST Response Body"
					description="Both request and response bodies captured"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts?test=response-body-post',
								{
									method: 'POST',
									headers: {
										'Content-Type': 'application/json',
									},
									body: JSON.stringify({
										title: 'Test',
										body: 'Verify both request and response bodies are on the span',
										userId: 1,
									}),
								},
							)
							const data = await response.json()
							console.log(
								'POST response body capture test:',
								data,
							)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>

				<TestButton
					title="Concurrent Response Bodies"
					description="5 parallel requests, all should have bodies"
					onClick={async () => {
						try {
							const promises = []
							for (let i = 1; i <= 5; i++) {
								promises.push(
									fetch(
										`https://jsonplaceholder.typicode.com/posts/${i}?test=response-body-concurrent-${i}`,
									),
								)
							}
							const responses = await Promise.all(promises)
							const data = await Promise.all(
								responses.map((r) => r.json()),
							)
							console.log(
								'Concurrent response body capture test:',
								`${data.length} responses with bodies`,
							)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>
			</TestSection>

			<TestSection
				title="Response Tests"
				description="Test different response types and status codes."
			>
				<TestButton
					title="Successful GET"
					description="200 OK response"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts/30?test=successful-get',
							)
							console.log('200 response status:', response.status)
							const data = await response.json()
							console.log('Response data:', data)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>

				<TestButton
					title="404 Not Found"
					description="Error response"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts/999999?test=not-found',
							)
							console.log('404 response status:', response.status)
							const data = await response.json()
							console.log('Response data:', data)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>

				<TestButton
					title="DELETE Request"
					description="Delete resource"
					onClick={async () => {
						try {
							const response = await fetch(
								'https://jsonplaceholder.typicode.com/posts/40?test=delete-request',
								{
									method: 'DELETE',
								},
							)
							console.log(
								'DELETE response status:',
								response.status,
							)
							const data = await response.json()
							console.log('DELETE response:', data)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>

				<TestButton
					title="Multiple Requests"
					description="Trigger 5 rapid requests"
					onClick={async () => {
						try {
							const promises = []
							for (let i = 51; i <= 55; i++) {
								promises.push(
									fetch(
										`https://jsonplaceholder.typicode.com/posts/${i}?test=batch-request-${i}`,
									),
								)
							}
							const responses = await Promise.all(promises)
							console.log(
								'All responses completed:',
								responses.length,
							)
							const data = await Promise.all(
								responses.map((r) => r.json()),
							)
							console.log('All response data:', data)
						} catch (e) {
							console.error('Request error:', e)
						}
					}}
				/>
			</TestSection>

			<section
				style={{
					marginTop: '2rem',
					padding: '1rem',
					backgroundColor: '#f5f5f5',
					borderRadius: '4px',
				}}
			>
				<h3>Testing Instructions</h3>
				<ol>
					<li>Click any button to trigger an HTTP request</li>
					<li>
						Check the browser console for request confirmation (some
						may fail due to CORS, that's okay)
					</li>
					<li>
						Verify in the observability backend that:
						<ul>
							<li>
								Sensitive headers (Authorization, Cookie, etc.)
								show [REDACTED]
							</li>
							<li>
								URLs with credentials are sanitized to
								REDACTED:REDACTED@domain
							</li>
							<li>
								Query params like sig, signature, awsAccessKeyId
								show REDACTED
							</li>
							<li>Safe headers and query params are preserved</li>
							<li>
								Request/response bodies are recorded as
								configured
							</li>
							<li>
								Response Body Capture: each span has an{' '}
								<code>http.response.body</code> attribute with
								the full JSON response (not just{' '}
								<code>http.response.body.size</code>)
							</li>
						</ul>
					</li>
				</ol>
			</section>
		</div>
	)
}
