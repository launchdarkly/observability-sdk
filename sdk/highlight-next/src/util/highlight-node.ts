import type { HighlightContext, NodeOptions } from '@highlight-run/node'
import { H as NodeH } from '@highlight-run/node'
import { isNodeJsRuntime } from './is-node-js-runtime'
import { HighlightInterface } from './types'

export type HighlightEnv = NodeOptions

export declare interface Metric {
	name: string
	value: number
	tags?: { name: string; value: string }[]
}

export const H: HighlightInterface = {
	...NodeH,
	init: (options: NodeOptions) => {
		if (isNodeJsRuntime()) {
			return NodeH.init(options)
		} else {
			throw new Error(
				`Highlight not registered due to unexpected runtime: NEXT_RUNTIME=${process.env.NEXT_RUNTIME}`,
			)
		}
	},
	initEdge: () => {
		throw new Error(
			'H.initEdge is not supported by the Node runtime. Try H.init instead.',
		)
	},
	isInitialized: () => NodeH.isInitialized(),
	metrics: (metrics: Metric[], opts?: HighlightContext) => {
		if (!opts?.secureSessionId) {
			return console.warn(
				'H.metrics session could not be inferred the handler context.',
			)
		}
		for (const m of metrics) {
			const tags = [...(m.tags ?? [])]
			if (opts.secureSessionId) {
				tags.push({
					name: 'highlight.session_id',
					value: opts.secureSessionId,
				})
			}
			if (opts.requestId) {
				tags.push({
					name: 'highlight.trace_id',
					value: opts.requestId,
				})
			}
			NodeH.recordMetric({
				...m,
				tags,
			})
		}
	},
}
