import { ReportDialog } from '@highlight-run/react'
import * as React from 'react'
import * as ReactDOM from 'react-dom/client'
import {
	createBrowserRouter,
	createRoutesFromElements,
	Route,
	RouterProvider,
	useRouteError,
} from 'react-router-dom'
import Root from './routes/root'
import Welcome from './routes/welcome'
import PrivacyDemo from './routes/privacy-demo'
import HttpTest from './routes/http-test'

function rootAction() {
	const contact = { name: 'hello' }
	return { contact }
}

function rootLoader() {
	const contact = { name: 'hello' }
	return { contact }
}

export function ErrorPage() {
	const error = useRouteError() as { statusText: string; data: string }
	return (
		<ReportDialog error={new Error(`${error.statusText}: ${error.data}`)} />
	)
}

const router = createBrowserRouter(
	createRoutesFromElements(
		<>
			<Route
				path="/"
				element={<Root />}
				loader={rootLoader}
				action={rootAction}
				ErrorBoundary={ErrorPage}
			>
				<Route>
					<Route index element={<Root />} />
				</Route>
			</Route>
			<Route path={'/welcome'} element={<Welcome />} />
			<Route path={'/privacy'} element={<PrivacyDemo />} />
			<Route path={'/http-test'} element={<HttpTest />} />
		</>,
	),
)

ReactDOM.createRoot(document.getElementById('root')!).render(
	<React.StrictMode>
		<RouterProvider router={router} />
	</React.StrictMode>,
)
