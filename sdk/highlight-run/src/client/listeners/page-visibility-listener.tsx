export const PageVisibilityListener = (
	callback: (isTabHidden: boolean) => void,
) => {
	const listener = () => {
		const tabState = document.hidden

		if (tabState) {
			callback(true)
		} else {
			callback(false)
		}
	}

	document.addEventListener('visibilitychange', listener)
	return () => document.removeEventListener('visibilitychange', listener)
}
