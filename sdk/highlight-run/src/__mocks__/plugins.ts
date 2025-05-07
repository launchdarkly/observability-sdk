// Mock implementations for plugins

// Common plugin functionality
export const getPluginVersion = () => {
	return '1.0.0'
}

export const createPlugin = (options: any = {}) => {
	return {
		name: 'highlight-plugin',
		version: getPluginVersion(),
		options,
		setup: () => {},
		teardown: () => {},
	}
}

// Observe plugin
export const createObservePlugin = (options: any) => {
	if (!options.projectId) {
		throw new Error('projectId is required for observe plugin')
	}

	return {
		name: '@highlight-run/observe',
		version: getPluginVersion(),
		options,
		_load: () => {},
		_setup: () => {},
		load: function (context: any) {
			this._load(context)
		},
		setup: function () {
			this._setup()
		},
		teardown: () => {},
	}
}

// Record plugin
export const createRecordPlugin = (options: any) => {
	if (!options.projectId) {
		throw new Error('projectId is required for record plugin')
	}

	return {
		name: '@highlight-run/record',
		version: getPluginVersion(),
		options,
		_load: () => {},
		_setup: () => {},
		load: function (context: any) {
			this._load(context)
		},
		setup: function () {
			this._setup()
		},
		teardown: () => {},
	}
}
