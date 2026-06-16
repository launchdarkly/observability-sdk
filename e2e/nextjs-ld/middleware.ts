import { observabilityMiddleware } from '@launchdarkly/observability-next/server'
import { NextResponse } from 'next/server'

export async function middleware(request: Request) {
	await observabilityMiddleware(request)
	return NextResponse.next()
}
