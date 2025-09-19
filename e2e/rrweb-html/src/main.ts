import { initialize as init } from 'launchdarkly-js-client-sdk'
import Observability from '@launchdarkly/observability'
import SessionReplay from '@launchdarkly/session-replay'
import { compressSync, strToU8 } from 'fflate'

const observabilitySettings: ConstructorParameters<typeof Observability>[0] = {
	networkRecording: {
		enabled: true,
		recordHeadersAndBody: true,
	},
	serviceName: 'e2e-rrweb-html',
	backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com/',
	otel: {
		otlpEndpoint: 'https://otel.observability.ld-stg.launchdarkly.com:4318',
	},
}

const sessionReplaySettings: ConstructorParameters<typeof SessionReplay>[0] = {
	debug: { clientInteractions: true, domRecording: true },
	privacySetting: 'none',
	serviceName: 'e2e-rrweb-html',
	backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com/',
	enableCanvasRecording: true,
	inlineImages: true,
}

const client = init(
	'548f6741c1efad40031b18ae',
	{ key: 'rrweb-html' },
	{
		plugins: [
			new Observability(observabilitySettings),
			new SessionReplay(sessionReplaySettings),
		],
		baseUrl: 'https://ld-stg.launchdarkly.com',
		eventsUrl: 'https://events-stg.launchdarkly.com',
	},
)

function generateSolidColorDataUrl(
	width: number,
	height: number,
	color: string,
): string {
	const canvas = document.createElement('canvas')
	canvas.width = width
	canvas.height = height
	const ctx = canvas.getContext('2d')
	if (!ctx) return ''
	ctx.fillStyle = color
	ctx.fillRect(0, 0, width, height)
	return canvas.toDataURL('image/png')
}

function setImage(color: string) {
	const img = document.getElementById('img-target') as HTMLImageElement | null
	if (!img) return
	img.src = generateSolidColorDataUrl(240, 160, color)
}

function setRandomImage() {
	const r = Math.floor(Math.random() * 255)
	const g = Math.floor(Math.random() * 255)
	const b = Math.floor(Math.random() * 255)
	setImage(`rgb(${r}, ${g}, ${b})`)
}

function wireUi() {
	document
		.getElementById('btn-red')
		?.addEventListener('click', () => setImage('red'))
	document
		.getElementById('btn-green')
		?.addEventListener('click', () => setImage('green'))
	document
		.getElementById('btn-blue')
		?.addEventListener('click', () => setImage('blue'))
	document
		.getElementById('btn-random')
		?.addEventListener('click', () => setRandomImage())
	// set default image
	setImage('gray')

	// canvas demo wiring
	const canvas = document.getElementById(
		'canvas-target',
	) as HTMLCanvasElement | null
	const ctx = canvas?.getContext('2d') || null

	function setCanvasColor(color: string) {
		if (!ctx || !canvas) return
		ctx.fillStyle = color
		ctx.fillRect(0, 0, canvas.width, canvas.height)
	}

	document
		.getElementById('btn-red')
		?.addEventListener('click', () => setCanvasColor('red'))
	document
		.getElementById('btn-green')
		?.addEventListener('click', () => setCanvasColor('green'))
	document
		.getElementById('btn-blue')
		?.addEventListener('click', () => setCanvasColor('blue'))
	document.getElementById('btn-random')?.addEventListener('click', () => {
		const r = Math.floor(Math.random() * 255)
		const g = Math.floor(Math.random() * 255)
		const b = Math.floor(Math.random() * 255)
		setCanvasColor(`rgb(${r}, ${g}, ${b})`)
	})

	setCanvasColor('lightgray')
}

// Wait for LD client ready then wire UI
client.waitUntilReady().then(() => {
	wireUi()
})
