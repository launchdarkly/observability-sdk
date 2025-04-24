import type { LDClientMin } from '../integrations/launchdarkly/types/LDClient'
import type { Observability } from './observability'
import type { Record } from './record'

export interface Client extends Record, Observability {
	registerLD: (client: LDClientMin) => void
}
