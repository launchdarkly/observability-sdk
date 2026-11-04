// based on: https://stackoverflow.com/questions/6390341/how-to-detect-if-url-has-changed-after-hash-in-javascript/52809105#52809105

/**
 * The single `history` wrap shared by every PathListener subscriber. Kept on
 * `window` so separately bundled plugins (observability, session replay)
 * share one wrap too. Without sharing, each subscription wrapped
 * `pushState`/`replaceState` again and every wrap dispatched its own
 * `locationchange`, so one navigation reached each subscriber once per wrap.
 */
type SharedHistoryWrap = {
	subscribers: number
	release: () => void
}

const SHARED_KEY = '__ldPathListenerHistoryWrap'

type WindowWithWrap = Window & { [SHARED_KEY]?: SharedHistoryWrap }

const installHistoryWrap = (): SharedHistoryWrap => {
	let active = true
	const dispatch = (type: string) => {
		if (!active) {
			return
		}
		window.dispatchEvent(new Event(type))
		window.dispatchEvent(new Event('locationchange'))
	}

	const initialPushState = history.pushState
	const pushState = function (this: History) {
		// @ts-ignore
		const ret = initialPushState.apply(this, arguments)
		dispatch('pushstate')
		return ret
	} as History['pushState']
	history.pushState = pushState

	const initialReplaceState = history.replaceState
	const replaceState = function (this: History) {
		// @ts-ignore
		const ret = initialReplaceState.apply(this, arguments)
		dispatch('replacestate')
		return ret
	} as History['replaceState']
	history.replaceState = replaceState

	const onPopState = () => {
		window.dispatchEvent(new Event('locationchange'))
	}
	window.addEventListener('popstate', onPopState)

	return {
		subscribers: 0,
		release: () => {
			active = false
			window.removeEventListener('popstate', onPopState)
			// Only unwrap when nothing wrapped on top of us; otherwise the
			// inert wrapper stays in the chain and just passes through.
			if (history.pushState === pushState) {
				history.pushState = initialPushState
			}
			if (history.replaceState === replaceState) {
				history.replaceState = initialReplaceState
			}
		},
	}
}

/**
 * Calls `callback` with the current URL now and on every URL change
 * (`pushState`, `replaceState`, back/forward). Returns an idempotent
 * unsubscribe.
 */
export const PathListener = (callback: (url: string) => void) => {
	callback(window.location.href)

	const w = window as WindowWithWrap
	const wrap = (w[SHARED_KEY] ??= installHistoryWrap())
	wrap.subscribers++

	const onLocationChange = function () {
		callback(window.location.href)
	}
	window.addEventListener('locationchange', onLocationChange)

	let subscribed = true
	return () => {
		if (!subscribed) {
			return
		}
		subscribed = false
		window.removeEventListener('locationchange', onLocationChange)
		wrap.subscribers--
		if (wrap.subscribers <= 0 && w[SHARED_KEY] === wrap) {
			wrap.release()
			delete w[SHARED_KEY]
		}
	}
}
