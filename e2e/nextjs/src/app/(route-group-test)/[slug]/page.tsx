import Link from 'next/link'

type Params = Promise<{ slug: string }>

export default async function AnotherPage(props: { params: Params }) {
	const params = await props.params
	return (
		<div>
			<h1>This is a route group slug page</h1>
			<p>Slug: {params?.slug}</p>
			<Link href="/">Go To Your Home</Link>
			<button
				onClick={() => {
					throw new Error('route group error')
				}}
			></button>
		</div>
	)
}
