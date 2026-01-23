import type { IntegrationOptions, SessionShortcutOptions } from './client'
import type {
	CommonOptions,
	PrivacySettingOption,
	SamplingStrategy,
} from './types'

export type RecordOptions = CommonOptions & {
	/**
	 * Specifies where the backend of the app lives. If specified, the SDK will attach the
	 * traceparent header to outgoing requests whose destination URLs match a substring
	 * or regexp from this list, so that backend errors can be linked back to the session.
	 * If 'true' is specified, all requests to the current domain will be matched.
	 * @example tracingOrigins: ['localhost', /^\//, 'backend.myapp.com']
	 */
	tracingOrigins?: boolean | (string | RegExp)[]
	/**
	 * If set, the SDK will not record when your app is not visible (in the background).
	 * By default, the SDK will record in the background.
	 * @default false
	 */
	disableBackgroundRecording?: boolean
	enableSegmentIntegration?: boolean
	/**
	 * Specifies the environment your application is running in.
	 * This is useful to distinguish whether your session was recorded on localhost or in production.
	 * @default 'production'
	 */
	environment?: 'development' | 'staging' | 'production' | string
	/**
	 * Specifies how much data the SDK should redact during recording.
	 * strict - the SDK will redact all text data, input fields, images and videos on the page.
	 * default - the SDK will redact text data on the page that resembles PII (based on regex patterns).
	 * none - the SDK will not redact any text data on the page.
	 * // Redacted text will be randomized. Instead of seeing "Hello World" in a recording, you will see "1fds1 j59a0".
	 * @see {@link https://launchdarkly.com/docs/sdk/features/session-replay-config#privacy} for more information.
	 */
	privacySetting?: PrivacySettingOption

	/**
	 * Enable masking all <input/> elements. Only applies if privacySetting is `none`.
	 */
	maskAllInputs?: boolean

	/**
	 * Customize the input element types that are masked. Only applies if privacySetting is `none`.
	 */
	maskInputOptions?: Partial<{
		color: boolean
		date: boolean
		'datetime-local': boolean
		email: boolean
		month: boolean
		number: boolean
		range: boolean
		search: boolean
		tel: boolean
		text: boolean
		time: boolean
		url: boolean
		week: boolean
		textarea: boolean
		select: boolean
		password: boolean
	}>

	/**
	 * Customize which elements' text should be masked by specifying a CSS class name or RegExp.
	 * Default class is 'highlight-mask'.
	 */
	maskTextClass?: string | RegExp

	/**
	 * Customize which elements' text should be masked via a CSS selector that will match the element
	 * and its descendants.
	 */
	maskTextSelector?: string

	/**
	 * Customize which elements should be blocked (not recorded) by specifying a class name or RegExp.
	 * Default is 'highlight-block'.
	 */
	blockClass?: string | RegExp

	/**
	 * Customize which elements should be blocked via a CSS selector.
	 */
	blockSelector?: string

	/**
	 * Customize which elements and their descendants should be ignored from DOM events by class.
	 * Default is 'highlight-ignore'.
	 */
	ignoreClass?: string

	/**
	 * Customize which elements should be ignored from DOM events via a CSS selector.
	 */
	ignoreSelector?: string

	/**
	 * Specifies whether to record canvas elements or not.
	 * @default false
	 */
	enableCanvasRecording?: boolean
	/**
	 * Configure the recording sampling options, eg. how frequently we record canvas updates.
	 */
	samplingStrategy?: SamplingStrategy
	/**
	 * Specifies whether to inline images into the recording.
	 * This means that images that are local to the client (eg. client-generated blob: urls)
	 * will be serialized into the recording and will be valid on replay.
	 * This will also use canvas snapshotting to inline <video> elements
	 * that use `src="blob:..."` data or webcam feeds (blank src) as <canvas> elements
	 * Only enable this if you are running into issues with client-local images.
	 * Will negatively affect performance.
	 * @default false
	 */
	inlineImages?: boolean
	/**
	 * Specifies whether to inline <video> elements into the recording.
	 * This means that video that are not accessible at a later time
	 * (eg., a signed URL that is short lived)
	 * will be serialized into the recording and will be valid on replay.
	 * Only enable this if you are running into issues with the normal serialization.
	 * Will negatively affect performance.
	 * @default false
	 */
	inlineVideos?: boolean
	/**
	 * Specifies whether to inline stylesheets into the recording.
	 * This means that stylesheets that are local to the client (eg. client-generated blob: urls)
	 * will be serialized into the recording and will be valid on replay.
	 * Only enable this if you are running into issues with client-local stylesheets.
	 * May negatively affect performance.
	 * @default true
	 */
	inlineStylesheet?: boolean
	/**
	 * Enables recording of cross-origin iframes. Should be set in both the parent window and
	 * in the cross-origin iframe.
	 * @default false
	 */
	recordCrossOriginIframe?: boolean
	integrations?: IntegrationOptions
	/**
	 * Specifies the keyboard shortcut to open the current session replay.
	 * @see {@link https://launchdarkly.com/docs/sdk/features/session-replay-config#retrieve-session-urls-on-the-client} for more information.
	 */
	sessionShortcut?: SessionShortcutOptions
	/**
	 * By default, data is serialized and send by the Web Worker. Set to `local` to force
	 * sending from the main js thread. Only use `local` for custom environments where Web Workers
	 * are not available (ie. Figma plugins).
	 */
	sendMode?: 'webworker' | 'local'
}
