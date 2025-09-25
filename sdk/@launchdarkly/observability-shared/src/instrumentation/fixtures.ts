// Portion of a real customer trace that was not getting named correctly.
export const exampleGenericHttpTrace = {
	timestamp: '2025-09-24T20:16:49.557Z',
	traceID: 'a9f6edc3676354edc3be5b525e719d78',
	spanID: '497b2d8767aeffb7',
	parentSpanID: '',
	projectID: 10007168,
	secureSessionID: '5mhnMixPKMFy2TfDAiM3MUDdCS3h',
	traceState: '',
	spanName: 'GET',
	spanKind: 'Client',
	duration: 405000000,
	serviceName: 'browser',
	serviceVersion: '',
	environment: '',
	hasErrors: false,
	traceAttributes: {
		browser: {
			language: 'en-US',
		},
		deployment: {
			environment: {
				name: 'production',
			},
		},
		http: {
			host: 'api.example.com',
			method: 'GET',
			request: {
				body: '',
			},
			response: {
				body: '',
			},
			response_content_length: '0',
			scheme: 'https',
			status_code: '200',
			url: 'https://api.example.com/v2/inbox/task_count',
			user_agent:
				'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36',
		},
		telemetry: {
			distro: {
				name: '@highlight-run/observability',
				version: '9.21.0',
			},
			sdk: {
				language: 'webjs',
				name: 'opentelemetry',
				version: '1.30.1',
			},
		},
		user_agent: {
			original:
				'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36',
		},
	},
	startTime: 0,
	statusCode: 'Unset',
	statusMessage: '',
	events: [
		{
			timestamp: '2025-09-24T20:16:49.557Z',
			name: 'open',
			attributes: {},
			__typename: 'TraceEvent',
		},
		{
			timestamp: '2025-09-24T20:16:49.5572Z',
			name: 'send',
			attributes: {},
			__typename: 'TraceEvent',
		},
		{
			timestamp: '2025-09-24T20:16:49.557299902Z',
			name: 'fetchStart',
			attributes: {},
			__typename: 'TraceEvent',
		},
		{
			timestamp: '2025-09-24T20:16:49.959399903Z',
			name: 'responseEnd',
			attributes: {},
			__typename: 'TraceEvent',
		},
		{
			timestamp: '2025-09-24T20:16:49.962Z',
			name: 'loaded',
			attributes: {},
			__typename: 'TraceEvent',
		},
	],
	__typename: 'Trace',
} as const
