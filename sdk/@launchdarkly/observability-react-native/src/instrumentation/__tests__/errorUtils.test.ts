import { describe, it, expect } from 'vitest'
import { extractRejectionDetails } from '../errorUtils'
import type {
	AxiosError,
	InternalAxiosRequestConfig,
	AxiosResponse,
} from 'axios'
import type { Attributes } from '@opentelemetry/api'

describe('extractRejectionDetails', () => {
	describe('Error instances', () => {
		it('should handle a standard Error object', () => {
			const error = new Error('Something went wrong')
			error.name = 'TestError'
			error.stack = 'Error: Something went wrong\n  at test.js:1:1'

			const result = extractRejectionDetails(error)

			expect(result.error).toBe(error)
			expect(result.attributes).toEqual({})
		})

		it('should handle TypeError', () => {
			const error = new TypeError('Invalid type')

			const result = extractRejectionDetails(error)

			expect(result.error).toBe(error)
			expect(result.error.name).toBe('TypeError')
			expect(result.attributes).toEqual({})
		})
	})

	describe('Axios errors', () => {
		it('should extract axios error with response data', () => {
			const mockConfig = {
				method: 'get',
				url: 'https://api.example.com/users/123',
			} as InternalAxiosRequestConfig

			const mockResponse: Partial<AxiosResponse> = {
				status: 404,
				statusText: 'Not Found',
				data: { message: 'Resource not found' },
				headers: {},
				config: mockConfig,
			}

			const axiosError = new Error(
				'Request failed with status code 404',
			) as AxiosError
			axiosError.name = 'AxiosError'
			;(axiosError as any).isAxiosError = true
			axiosError.response = mockResponse as AxiosResponse
			axiosError.config = mockConfig
			axiosError.code = 'ERR_BAD_REQUEST'

			const result = extractRejectionDetails(axiosError)

			expect(result.error).toBe(axiosError)
			expect(result.attributes).toEqual({
				'http.is_axios_error': true,
				'http.status_code': 404,
				'http.status_text': 'Not Found',
				'http.response_data': '{"message":"Resource not found"}',
				'http.method': 'GET',
				'http.url': 'https://api.example.com/users/123',
				'http.error_code': 'ERR_BAD_REQUEST',
			})
		})

		it('should handle axios error with string response data', () => {
			const mockConfig = {
				method: 'post',
				url: 'https://api.example.com/data',
			} as InternalAxiosRequestConfig

			const mockResponse: Partial<AxiosResponse> = {
				status: 500,
				statusText: 'Internal Server Error',
				data: 'Server error occurred',
				headers: {},
				config: mockConfig,
			}

			const axiosError = new Error('Request failed') as AxiosError
			;(axiosError as any).isAxiosError = true
			axiosError.response = mockResponse as AxiosResponse
			axiosError.config = mockConfig

			const result = extractRejectionDetails(axiosError)

			expect(result.attributes).toMatchObject({
				'http.is_axios_error': true,
				'http.status_code': 500,
				'http.response_data': 'Server error occurred',
			})
		})

		it('should handle axios error without response', () => {
			const mockConfig = {
				method: 'delete',
				url: 'https://api.example.com/resource',
			} as InternalAxiosRequestConfig

			const axiosError = new Error('Network Error') as AxiosError
			;(axiosError as any).isAxiosError = true
			axiosError.config = mockConfig
			axiosError.code = 'ERR_NETWORK'

			const result = extractRejectionDetails(axiosError)

			expect(result.attributes).toEqual({
				'http.is_axios_error': true,
				'http.method': 'DELETE',
				'http.url': 'https://api.example.com/resource',
				'http.error_code': 'ERR_NETWORK',
			})
		})

		it('should handle axios error with non-stringifiable response data', () => {
			const circularData: any = { prop: 'value' }
			circularData.circular = circularData

			const mockConfig = {} as InternalAxiosRequestConfig

			const mockResponse: Partial<AxiosResponse> = {
				status: 400,
				statusText: 'Bad Request',
				data: circularData,
				headers: {},
				config: mockConfig,
			}

			const axiosError = new Error('Bad request') as AxiosError
			;(axiosError as any).isAxiosError = true
			axiosError.response = mockResponse as AxiosResponse

			const result = extractRejectionDetails(axiosError)

			expect(result.attributes['http.is_axios_error']).toBe(true)
			expect(result.attributes['http.response_data']).toBeUndefined()
		})

		it('should handle axios timeout error', () => {
			const mockConfig = {
				method: 'get',
				url: 'https://api.example.com/slow',
			} as InternalAxiosRequestConfig

			const axiosError = new Error(
				'timeout of 5000ms exceeded',
			) as AxiosError
			;(axiosError as any).isAxiosError = true
			axiosError.config = mockConfig
			axiosError.code = 'ECONNABORTED'

			const result = extractRejectionDetails(axiosError)

			expect(result.attributes).toEqual({
				'http.is_axios_error': true,
				'http.method': 'GET',
				'http.url': 'https://api.example.com/slow',
				'http.error_code': 'ECONNABORTED',
			})
		})
	})

	describe('Fetch/Network errors', () => {
		it('should detect fetch errors', () => {
			const fetchError = new TypeError('Failed to fetch')

			const result = extractRejectionDetails(fetchError)

			expect(result.error).toBe(fetchError)
			expect(result.attributes).toEqual({
				'http.is_fetch_error': true,
			})
		})

		it('should detect network errors', () => {
			const networkError = new TypeError('Network request failed')

			const result = extractRejectionDetails(networkError)

			expect(result.error).toBe(networkError)
			expect(result.attributes).toEqual({
				'http.is_fetch_error': true,
			})
		})

		it('should not mark non-network TypeErrors as fetch errors', () => {
			const typeError = new TypeError('undefined is not a function')

			const result = extractRejectionDetails(typeError)

			expect(result.attributes).toEqual({})
		})
	})

	describe('Plain objects', () => {
		it('should handle object with message property', () => {
			const rejection = {
				message: 'Custom error message',
				code: 'ERR_CUSTOM',
				details: 'Some additional details',
			}

			const result = extractRejectionDetails(rejection)

			expect(result.error).toBeInstanceOf(Error)
			expect(result.error.message).toBe('Custom error message')
			expect(result.error.name).toBe('UnhandledRejection')
			expect(result.attributes).toEqual({
				'rejection.message': 'Custom error message',
				'rejection.code': 'ERR_CUSTOM',
				'rejection.details': 'Some additional details',
			})
		})

		it('should handle object with error property', () => {
			const rejection = {
				error: 'Something went wrong',
				status: 500,
			}

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Something went wrong')
			expect(result.attributes).toEqual({
				'rejection.error': 'Something went wrong',
				'rejection.status': 500,
			})
		})

		it('should handle object with description property', () => {
			const rejection = {
				description: 'Error description',
				timestamp: 1234567890,
			}

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Error description')
			expect(result.attributes).toEqual({
				'rejection.description': 'Error description',
				'rejection.timestamp': 1234567890,
			})
		})

		it('should handle object with name property', () => {
			const rejection = {
				name: 'CustomError',
				message: 'Custom message',
			}

			const result = extractRejectionDetails(rejection)

			expect(result.error.name).toBe('CustomError')
			expect(result.error.message).toBe('Custom message')
		})

		it('should handle object with stack property', () => {
			const customStack = 'Error: Custom\n  at test.js:1:1'
			const rejection = {
				message: 'Error with stack',
				stack: customStack,
			}

			const result = extractRejectionDetails(rejection)

			expect(result.error.stack).toBe(customStack)
		})

		it('should handle object with nested object properties', () => {
			const rejection = {
				message: 'Error with metadata',
				metadata: { userId: '123', requestId: 'abc' },
				count: 5,
			}

			const result = extractRejectionDetails(rejection)

			expect(result.attributes).toEqual({
				'rejection.message': 'Error with metadata',
				'rejection.metadata': '{"userId":"123","requestId":"abc"}',
				'rejection.count': 5,
			})
		})

		it('should skip function properties', () => {
			const rejection = {
				message: 'Error with function',
				handler: () => {},
				code: 'ERR_TEST',
			}

			const result = extractRejectionDetails(rejection)

			expect(result.attributes).toEqual({
				'rejection.message': 'Error with function',
				'rejection.code': 'ERR_TEST',
			})
		})

		it('should handle object with non-stringifiable nested object', () => {
			const circularObj: any = { prop: 'value' }
			circularObj.circular = circularObj

			const rejection = {
				message: 'Error with circular ref',
				data: circularObj,
			}

			const result = extractRejectionDetails(rejection)

			expect(result.attributes['rejection.message']).toBe(
				'Error with circular ref',
			)
			expect(result.attributes['rejection.data']).toBeUndefined()
		})

		it('should use default message for object without message-like properties', () => {
			const rejection = {
				code: 'ERR_UNKNOWN',
				timestamp: 123456,
			}

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Promise rejected with object')
		})

		it('should handle empty object', () => {
			const rejection = {}

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Promise rejected with object')
			expect(result.error.name).toBe('UnhandledRejection')
			expect(result.attributes).toEqual({})
		})
	})

	describe('Primitives', () => {
		it('should handle string rejection', () => {
			const rejection = 'Error string'

			const result = extractRejectionDetails(rejection)

			expect(result.error).toBeInstanceOf(Error)
			expect(result.error.message).toBe(
				'Promise rejected with string: Error string',
			)
			expect(result.error.name).toBe('UnhandledRejection')
			expect(result.attributes).toEqual({
				'rejection.type': 'string',
				'rejection.value': 'Error string',
			})
		})

		it('should handle number rejection', () => {
			const rejection = 42

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe(
				'Promise rejected with number: 42',
			)
			expect(result.attributes).toEqual({
				'rejection.type': 'number',
				'rejection.value': '42',
			})
		})

		it('should handle boolean rejection (true)', () => {
			const rejection = true

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe(
				'Promise rejected with boolean: true',
			)
			expect(result.attributes).toEqual({
				'rejection.type': 'boolean',
				'rejection.value': 'true',
			})
		})

		it('should handle boolean rejection (false)', () => {
			const rejection = false

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe(
				'Promise rejected with boolean: false',
			)
			expect(result.attributes).toEqual({
				'rejection.type': 'boolean',
				'rejection.value': 'false',
			})
		})

		it('should handle null rejection', () => {
			const rejection = null

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Promise rejected with null')
			expect(result.attributes).toEqual({
				'rejection.type': 'object',
				'rejection.value': 'null',
			})
		})

		it('should handle undefined rejection', () => {
			const rejection = undefined

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Promise rejected with undefined')
			expect(result.attributes).toEqual({
				'rejection.type': 'undefined',
				'rejection.value': 'undefined',
			})
		})

		it('should handle zero as rejection', () => {
			const rejection = 0

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Promise rejected with number: 0')
			expect(result.attributes).toEqual({
				'rejection.type': 'number',
				'rejection.value': '0',
			})
		})

		it('should handle empty string rejection', () => {
			const rejection = ''

			const result = extractRejectionDetails(rejection)

			expect(result.error.message).toBe('Promise rejected with string: ')
			expect(result.attributes).toEqual({
				'rejection.type': 'string',
				'rejection.value': '',
			})
		})
	})

	describe('Edge cases', () => {
		it('should return correct type for all attributes', () => {
			const rejection = {
				message: 'Test',
				code: 123,
				active: true,
			}

			const result = extractRejectionDetails(rejection)

			// Verify attributes conform to Attributes type (string | number | boolean values)
			const attributes: Attributes = result.attributes
			expect(typeof attributes['rejection.message']).toBe('string')
			expect(typeof attributes['rejection.code']).toBe('number')
			expect(typeof attributes['rejection.active']).toBe('boolean')
		})
	})
})
