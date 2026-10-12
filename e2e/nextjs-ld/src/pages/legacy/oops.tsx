// Thrown during render — the custom _error page records it
// (pageRouterCustomErrorHandler) and Next renders the _error component.
// getServerSideProps forces SSR so the throw happens per-request, not in the
// build's prerender.
export default function OopsPage() {
	throw new Error('Pages Router SSR render error (/legacy/oops)')
}

export async function getServerSideProps() {
	return { props: {} }
}
