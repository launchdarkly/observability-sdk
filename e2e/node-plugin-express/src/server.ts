import { init, LDMultiKindContext } from '@launchdarkly/node-server-sdk'
import { Observability, LDObserve } from '@launchdarkly/observability-node'
import express, { Express, NextFunction, Request, Response } from 'express'

const client = init(process.env.LAUNCHDARKLY_SDK_KEY ?? '', {
	plugins: [
		new Observability({
			serviceName: 'ryan-test-2',
		}),
	],
})

const app: Express = express()
const port = 3000

interface User {}

const userRepository = {
	findAll: async (): Promise<User[]> => {
		return []
	},
}

app.get('/start-span-example', (req: Request, res: Response) => {
	const { span } = LDObserve.startWithHeaders('example-span-a', req.headers)

	LDObserve.setAttributes({
		'example-attribute': 'example-value',
	})

	res.send('Hello World')
	span.end()
})

app.get('/run-span-example', async (req: Request, res: Response) => {
	await LDObserve.runWithHeaders('example-span-b', req.headers, (span) => {
		LDObserve.setAttributes({
			'example-attribute': 'example-value',
		})

		res.send('Hello World')
	})
})

app.get('/users', async (req: Request, res: Response) => {
	const users = await userRepository.findAll()
	res.json(users)
})

app.get('/error', async (req: Request, res: Response) => {
	LDObserve.recordError(new Error('test error'), undefined, undefined)
	res.status(500)
	res.send()
})

app.get('/user', async (req: Request, res: Response, next: NextFunction) => {
	// Evaluate something just to get the span event.
	const useMongo = await client.boolVariation(
		'useMongo',
		{ kind: 'request', key: 'test' },
		false,
	)

	console.log('test')

	if (useMongo) {
		throw new Error('Mongo is not supported')
	}

	res.status(200)
	res.json({})
})

app.get('/multi', async (req: Request, res: Response) => {
	const flag = await client.boolVariation(
		'my-boolean-flag',
		{ kind: 'router', key: 'service-name' },
		false,
	)
	const flag2 = await client.stringVariationDetail(
		'string-variation',
		{
			kind: 'multi',
			user: { key: 'sally' },
			org: {
				key: 'martmart',
			},
		},
		'default',
	)

	res.status(500)
	if (flag) {
		res.send('Flag enabled')
	} else {
		res.send('Flag disabled')
	}
})

async function asyncSleep(msTime: number) {
	return new Promise<void>((resolve) => {
		setTimeout(() => {
			resolve()
		}, msTime)
	})
}

app.get('/sleep', async (req: Request, res: Response) => {
	const userKey = req.query['key'] ?? 'bob'

	const context: LDMultiKindContext = {
		kind: 'multi',
		user: { key: userKey as string },
		service: { key: 'router' },
	}
	const msTime = await client.numberVariation('variable-sleep', context, 0)
	await asyncSleep(msTime)
	res.send('operation complete')
})

app.use((err: Error, req: Request, res: Response, next: NextFunction) => {
	res.status(500)
	res.send()
})

app.listen(port, () => {
	console.log(`[Server]: I am running at https://localhost:${port}`)
})
