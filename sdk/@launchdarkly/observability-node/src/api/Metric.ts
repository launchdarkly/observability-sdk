export declare interface Metric {
	name: string;
	value: number;
	tags?: { name: string; value: string; }[];
}
