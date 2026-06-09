import { getElementSelector } from '../../utils/dom'

export const ClickListener = (
	callback: (targetSelector: string, event: MouseEvent) => void,
) => {
	const recordClick = (event: MouseEvent) => {
		if (event.target) {
			const element = event.target as Element
			const targetSelector = getElementSelector(element)

			callback(targetSelector, event)
		}
	}
	// Listen in the capture phase so clicks are still recorded when app code
	// calls event.stopPropagation() before the event bubbles back up to window.
	// This mirrors how rrweb records mouse interactions (capture phase) and
	// prevents the custom `Click` event from being silently dropped.
	const options: AddEventListenerOptions = { capture: true }
	window.addEventListener('click', recordClick, options)
	return () => window.removeEventListener('click', recordClick, options)
}
