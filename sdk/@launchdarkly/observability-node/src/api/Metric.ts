export declare interface Metric {
	name: string
	value: number
	/** UCUM / OTel unit for the instrument (e.g. `ms`, `By`). */
	unit?: string
	tags?: { name: string; value: string }[]
}
