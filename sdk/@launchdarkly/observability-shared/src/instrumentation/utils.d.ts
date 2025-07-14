import { TracingOrigins } from './types'
export declare const shouldNetworkRequestBeRecorded: (
	url: string,
	internalEndpoints: string[],
	_tracingOrigins?: boolean | (string | RegExp)[],
) => boolean
export declare const shouldNetworkRequestBeTraced: (
	url: string,
	tracingOrigins: boolean | (string | RegExp)[],
	urlBlocklist: string[],
) => boolean
export declare const shouldRecordRequest: (
	url: string,
	internalEndpoints: string[],
	tracingOrigins: TracingOrigins,
	urlBlocklist: string[],
) => boolean
export declare function getCorsUrlsPattern(
	tracingOrigins: TracingOrigins,
): RegExp | RegExp[]
export declare const getSpanName: (
	url: string,
	method: string,
	body?:
		| Request['body']
		| RequestInit['body']
		| XMLHttpRequest['responseText'],
) => string
//# sourceMappingURL=utils.d.ts.map
