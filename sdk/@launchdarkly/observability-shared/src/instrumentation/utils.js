import { parse } from 'graphql';
import { isLDContextUrl } from '../launchdarkly/urlFilters';
const isLaunchDarklyEventsNetworkResource = (name) => {
    return ['events.ld.catamorphic.com', 'events.launchdarkly.com'].some((backendUrl) => name.toLocaleLowerCase().includes(backendUrl));
};
const isLaunchDarklyEvalNetworkResource = (name) => {
    return isLDContextUrl(name);
};
/**
 * Returns true if the name is a internal network resource.
 * This is used to filter out internal requests/responses from showing up on end
 * application's network resources.
 */
const isInternalNetworkResourceFilter = (name, internalEndpoints) => internalEndpoints.some((backendUrl) => name.toLocaleLowerCase().includes(backendUrl));
// Determines whether we store the network request and show it in the session
// replay, including the body and headers.
export const shouldNetworkRequestBeRecorded = (url, internalEndpoints, _tracingOrigins) => {
    return (!isInternalNetworkResourceFilter(url, internalEndpoints) &&
        !isLaunchDarklyEvalNetworkResource(url) &&
        !isLaunchDarklyEventsNetworkResource(url));
};
// Determines whether we want to assign trace propagation headers to a request.
export const shouldNetworkRequestBeTraced = (url, tracingOrigins, urlBlocklist) => {
    if (urlBlocklist.some((blockedUrl) => url.toLowerCase().includes(blockedUrl))) {
        return false;
    }
    const patterns = getCorsUrlsPattern(tracingOrigins);
    if (Array.isArray(patterns)) {
        return patterns.some((pattern) => pattern.test(url));
    }
    return patterns.test(url);
};
export const shouldRecordRequest = (url, internalEndpoints, tracingOrigins, urlBlocklist) => {
    const shouldRecordHeaderAndBody = !urlBlocklist?.some((blockedUrl) => url.toLowerCase().includes(blockedUrl));
    if (!shouldRecordHeaderAndBody) {
        return false;
    }
    return shouldNetworkRequestBeRecorded(url, internalEndpoints, tracingOrigins);
};
// Helper to convert tracingOrigins to a RegExp or array of RegExp
export function getCorsUrlsPattern(tracingOrigins) {
    if (tracingOrigins === true) {
        const patterns = [/localhost/, /^\//];
        if (typeof window !== 'undefined' && window.location?.host) {
            patterns.push(new RegExp(window.location.host));
        }
        return patterns;
    }
    else if (Array.isArray(tracingOrigins)) {
        return tracingOrigins.map((pattern) => typeof pattern === 'string' ? new RegExp(pattern) : pattern);
    }
    return /^$/; // Match nothing if tracingOrigins is false or undefined
}
export const getSpanName = (url, method, body) => {
    const urlObject = new URL(url);
    let spanName = `${method.toUpperCase()} - ${urlObject.host}${urlObject.pathname}`;
    if (!body) {
        return spanName;
    }
    try {
        const parsedBody = typeof body === 'string' ? JSON.parse(body) : body;
        if (parsedBody && parsedBody.query) {
            const query = parse(parsedBody.query);
            const queryName = query.definitions[0]?.kind === 'OperationDefinition'
                ? query.definitions[0]?.name?.value
                : undefined;
            if (queryName) {
                spanName = `${queryName} (GraphQL: ${urlObject.host + urlObject.pathname})`;
            }
        }
    }
    catch {
        // Ignore errors from JSON parsing
    }
    return spanName;
};
