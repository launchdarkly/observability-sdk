import type { Observe } from './observe'
import type { Record } from './record'

export interface Client extends Record, Observe {}
