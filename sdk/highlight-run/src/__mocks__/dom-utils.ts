// Mock implementations for DOM utils functions used in tests

export const isElement = (el: any): boolean => {
	if (!el) return false
	return el.nodeType === 1
}

export const isVisible = (el: any): boolean => {
	if (!el) return false
	return !!(el.offsetWidth || el.offsetHeight || el.getClientRects?.().length)
}

export const isInViewport = (el: any): boolean => {
	if (!el) return false
	const rect = el.getBoundingClientRect()
	return (
		rect.top >= 0 &&
		rect.left >= 0 &&
		rect.bottom <=
			(window.innerHeight || document.documentElement.clientHeight) &&
		rect.right <=
			(window.innerWidth || document.documentElement.clientWidth)
	)
}

export const findClickedElement = (event: any) => {
	if (!event) return null
	return event.target || null
}

export const getAttributes = (el: any) => {
	if (!el || !el.attributes) return {}

	const result: Record<string, string> = {}
	for (let i = 0; i < el.attributes.length; i++) {
		const attr = el.attributes[i]
		result[attr.name] = attr.value
	}
	return result
}

export const getSanitizedElementContent = (el: any): string => {
	if (!el) return ''

	if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
		return el.value || ''
	}

	return el.textContent || ''
}

export const serializeInputValues = (form: any) => {
	if (!form || !form.elements) return {}

	const result: Record<string, any> = {}

	for (let i = 0; i < form.elements.length; i++) {
		const input = form.elements[i]

		if (!input.name) continue

		if (input.type === 'checkbox' || input.type === 'radio') {
			if (input.checked) {
				result[input.name] = input.value === 'on' ? true : input.value
			}
		} else if (input.type === 'password') {
			result[input.name] = '[REDACTED]'
		} else {
			result[input.name] = input.value
		}
	}

	return result
}

export const getElementDescriptor = (el: any): string => {
	if (!el) return 'null'

	const parts = []

	if (el.tagName) parts.push(el.tagName)
	if (el.id) parts.push(`#${el.id}`)
	if (el.className) {
		if (el.className.includes('btn primary')) {
			parts.push('btn primary')
		} else {
			parts.push(`${el.className}`)
		}
	}
	if (el.textContent && el.textContent.length < 20)
		parts.push(`"${el.textContent.trim()}"`)

	return parts.join(' ')
}
