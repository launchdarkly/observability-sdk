import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import * as domUtils from '../__mocks__/dom-utils'

describe('DOM Utils', () => {
	const originalWindow = global.window
	const originalDocument = global.document

	// Mock necessary DOM objects
	beforeEach(() => {
		global.window = {
			...originalWindow,
			getComputedStyle: vi.fn().mockReturnValue({
				getPropertyValue: vi.fn().mockReturnValue('value'),
			}),
		} as any

		global.document = {
			...originalDocument,
			createElement: vi.fn((tagName) => {
				if (tagName === 'div') {
					return {
						className: '',
						style: {},
						setAttribute: vi.fn(),
						appendChild: vi.fn(),
						contains: vi.fn().mockReturnValue(true),
						getBoundingClientRect: vi.fn().mockReturnValue({
							top: 0,
							left: 0,
							width: 100,
							height: 100,
							right: 100,
							bottom: 100,
						}),
					}
				} else if (tagName === 'canvas') {
					return {
						getContext: vi.fn().mockReturnValue({
							drawImage: vi.fn(),
							fillRect: vi.fn(),
							clearRect: vi.fn(),
							getImageData: vi.fn().mockReturnValue({
								data: new Uint8ClampedArray(400),
							}),
						}),
						width: 0,
						height: 0,
						style: {},
						toDataURL: vi
							.fn()
							.mockReturnValue('data:image/png;base64,test'),
					}
				}
				return {}
			}),
			body: {
				appendChild: vi.fn(),
				removeChild: vi.fn(),
				contains: vi.fn().mockReturnValue(true),
			},
			documentElement: {
				clientWidth: 1024,
				clientHeight: 768,
			},
		} as any
	})

	afterEach(() => {
		global.window = originalWindow
		global.document = originalDocument
		vi.clearAllMocks()
	})

	describe('isElement', () => {
		it('should return true for valid HTMLElements', () => {
			const mockElement = {
				nodeType: 1,
			}
			expect(domUtils.isElement(mockElement as any)).toBe(true)
		})

		it('should return false for non-elements', () => {
			const mockElement = {
				nodeType: 3, // Text node
			}
			expect(domUtils.isElement(mockElement as any)).toBe(false)
			expect(domUtils.isElement(null)).toBe(false)
			expect(domUtils.isElement(undefined)).toBe(false)
			expect(domUtils.isElement({})).toBe(false)
		})
	})

	describe('isVisible', () => {
		it('should return true for visible elements', () => {
			const mockElement = {
				nodeType: 1,
				offsetWidth: 100,
				offsetHeight: 100,
				style: {
					display: 'block',
					visibility: 'visible',
				},
				getClientRects: vi
					.fn()
					.mockReturnValue([{ width: 10, height: 10 }]),
			}

			expect(domUtils.isVisible(mockElement as any)).toBe(true)
		})

		it('should return false for invisible elements', () => {
			const mockElement = {
				nodeType: 1,
				offsetWidth: 0,
				offsetHeight: 0,
				style: {
					display: 'none',
					visibility: 'hidden',
				},
				getClientRects: vi.fn().mockReturnValue([]),
			}

			expect(domUtils.isVisible(mockElement as any)).toBe(false)
		})

		it('should handle elements with no getClientRects', () => {
			const mockElement = {
				nodeType: 1,
				offsetWidth: 100,
				offsetHeight: 100,
				style: {
					display: 'block',
					visibility: 'visible',
				},
			}

			expect(domUtils.isVisible(mockElement as any)).toBe(true)
		})
	})

	describe('isInViewport', () => {
		it('should return true for elements within viewport', () => {
			const mockElement = {
				getBoundingClientRect: vi.fn().mockReturnValue({
					top: 100,
					left: 100,
					bottom: 200,
					right: 200,
				}),
			}

			expect(domUtils.isInViewport(mockElement as any)).toBe(true)
		})

		it('should return false for elements outside viewport', () => {
			const mockElement = {
				getBoundingClientRect: vi.fn().mockReturnValue({
					top: -200,
					left: -200,
					bottom: -100,
					right: -100,
				}),
			}

			expect(domUtils.isInViewport(mockElement as any)).toBe(false)
		})
	})

	describe('findClickedElement', () => {
		it('should find the clicked element', () => {
			const targetElement = { tagName: 'BUTTON' }
			const mockEvent = {
				target: targetElement,
			}

			expect(domUtils.findClickedElement(mockEvent as any)).toBe(
				targetElement,
			)
		})

		it('should return null for null events', () => {
			expect(domUtils.findClickedElement(null)).toBeNull()
		})

		it('should return null for events with no target', () => {
			expect(domUtils.findClickedElement({} as any)).toBeNull()
		})
	})

	describe('getAttributes', () => {
		it('should get all attributes from an element', () => {
			const mockElement = {
				attributes: [
					{ name: 'id', value: 'test-id' },
					{ name: 'class', value: 'test-class' },
					{ name: 'data-test', value: 'test-data' },
				],
			}

			const attributes = domUtils.getAttributes(mockElement as any)

			expect(attributes).toEqual({
				id: 'test-id',
				class: 'test-class',
				'data-test': 'test-data',
			})
		})

		it('should return empty object for elements with no attributes', () => {
			const mockElement = {
				attributes: [],
			}

			expect(domUtils.getAttributes(mockElement as any)).toEqual({})
		})

		it('should handle null elements', () => {
			expect(domUtils.getAttributes(null)).toEqual({})
		})
	})

	describe('getSanitizedElementContent', () => {
		it('should handle input elements', () => {
			const mockElement = {
				tagName: 'INPUT',
				type: 'text',
				value: 'test value',
				attributes: [],
			}

			expect(
				domUtils.getSanitizedElementContent(mockElement as any),
			).toBe('test value')
		})

		it('should handle textarea elements', () => {
			const mockElement = {
				tagName: 'TEXTAREA',
				value: 'multi-line\ntext area',
				attributes: [],
			}

			expect(
				domUtils.getSanitizedElementContent(mockElement as any),
			).toBe('multi-line\ntext area')
		})

		it('should handle standard elements with textContent', () => {
			const mockElement = {
				tagName: 'DIV',
				textContent: 'div content',
				attributes: [],
			}

			expect(
				domUtils.getSanitizedElementContent(mockElement as any),
			).toBe('div content')
		})

		it('should handle null elements', () => {
			expect(domUtils.getSanitizedElementContent(null)).toBe('')
		})
	})

	describe('serializeInputValues', () => {
		it('should serialize form element inputs', () => {
			const mockForm = {
				elements: [
					{
						tagName: 'INPUT',
						name: 'username',
						value: 'testuser',
						type: 'text',
					},
					{
						tagName: 'INPUT',
						name: 'password',
						value: 'password123',
						type: 'password',
					},
					{ tagName: 'SELECT', name: 'country', value: 'US' },
					{
						tagName: 'TEXTAREA',
						name: 'comment',
						value: 'Test comment',
					},
				],
			}

			const serialized = domUtils.serializeInputValues(mockForm as any)

			expect(serialized).toEqual({
				username: 'testuser',
				password: '[REDACTED]',
				country: 'US',
				comment: 'Test comment',
			})
		})

		it('should handle forms with checkboxes and radio buttons', () => {
			const mockForm = {
				elements: [
					{
						tagName: 'INPUT',
						name: 'subscribe',
						checked: true,
						type: 'checkbox',
						value: 'on',
					},
					{
						tagName: 'INPUT',
						name: 'gender',
						checked: true,
						type: 'radio',
						value: 'male',
					},
					{
						tagName: 'INPUT',
						name: 'gender',
						checked: false,
						type: 'radio',
						value: 'female',
					},
				],
			}

			const serialized = domUtils.serializeInputValues(mockForm as any)

			expect(serialized).toEqual({
				subscribe: true,
				gender: 'male',
			})
		})

		it('should handle forms with no elements', () => {
			const mockForm = {
				elements: [],
			}

			expect(domUtils.serializeInputValues(mockForm as any)).toEqual({})
		})
	})

	describe('getElementDescriptor', () => {
		it('should create descriptor for typical elements', () => {
			const mockElement = {
				tagName: 'BUTTON',
				id: 'submit-btn',
				className: 'btn primary',
				textContent: 'Submit',
				getAttribute: vi.fn((attr) => {
					if (attr === 'data-testid') return 'submit-button'
					return null
				}),
			}

			const descriptor = domUtils.getElementDescriptor(mockElement as any)

			expect(descriptor).toContain('BUTTON')
			expect(descriptor).toContain('submit-btn')
			expect(descriptor).toContain('btn primary')
			expect(descriptor).toContain('Submit')
		})

		it('should create descriptor for input elements', () => {
			const mockElement = {
				tagName: 'INPUT',
				type: 'text',
				id: 'username',
				placeholder: 'Enter username',
				className: 'form-control',
				getAttribute: vi.fn(() => null),
			}

			const descriptor = domUtils.getElementDescriptor(mockElement as any)

			expect(descriptor).toContain('INPUT')
			expect(descriptor).toContain('username')
			expect(descriptor).toContain('form-control')
		})

		it('should handle null elements', () => {
			expect(domUtils.getElementDescriptor(null)).toBe('null')
		})
	})
})
