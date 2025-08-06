import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import LDProvider from './LDProvider.tsx'
;(async () => {
	const Provider = await LDProvider
	createRoot(document.getElementById('root')!).render(
		<StrictMode>
			<Provider>
				<App />
			</Provider>
		</StrictMode>,
	)
})()
