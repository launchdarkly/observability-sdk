import { ReportDialog as ReactReportDialog } from '@highlight-run/react'
import React from 'react'

/**
 * @deprecated Session feedback is no longer supported, so feedback submitted
 * through the report dialog is discarded.
 */
export function ReportDialog() {
	return typeof window === 'object' ? <ReactReportDialog /> : null
}
