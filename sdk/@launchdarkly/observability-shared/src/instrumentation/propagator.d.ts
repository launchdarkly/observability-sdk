import { W3CTraceContextPropagator } from '@opentelemetry/core';
import * as api from '@opentelemetry/api';
import type { TracingOrigins } from './types';
type CustomTraceContextPropagatorConfig = {
    internalEndpoints: string[];
    tracingOrigins: TracingOrigins;
    urlBlocklist: string[];
};
export declare class CustomTraceContextPropagator extends W3CTraceContextPropagator {
    private internalEndpoints;
    private tracingOrigins;
    private urlBlocklist;
    constructor(config: CustomTraceContextPropagatorConfig);
    inject(context: api.Context, carrier: unknown, setter: api.TextMapSetter): void;
}
export {};
//# sourceMappingURL=propagator.d.ts.map