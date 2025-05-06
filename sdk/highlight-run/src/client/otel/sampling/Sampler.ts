import { Attributes } from "@opentelemetry/api";
import { ReadableSpan } from "@opentelemetry/sdk-trace-base";

export interface SamplingResult {
    /**
     * Whether the span should be sampled.
     */
    sample: boolean;

    /**
     * The attributes to add to the span.
     */
    attributes?: Attributes;
}

export interface Sampler {
    shouldSample(span: ReadableSpan): SamplingResult;
}
