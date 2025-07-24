import Cookies from 'js-cookie'
import { SESSION_PUSH_THRESHOLD } from '../constants/sessions'
import { internalLogOnce } from '../../sdk/util'

export enum LocalStorageKeys {
	CLIENT_ID = 'highlightClientID',
}

type Mode = 'localStorage' | 'sessionStorage'

let mode: Mode = 'localStorage'
let cookieWriteEnabled: boolean = true

class Storage {
	private storage: { [key: string]: string } = {}

	public get length(): number {
		return Object.keys(this.storage).length
	}

	public key(index: number): string | null {
		const keys = Object.keys(this.storage)
		return keys[index] ?? null
	}

	public getItem(key: string) {
		return this.storage[key] ?? ''
	}
	public setItem(key: string, value: string) {
		this.storage[key] = value
	}
	public removeItem(key: string) {
		delete this.storage[key]
	}
}

export class CookieStorage {
	public getItem(key: string) {
		return Cookies.get(key) ?? ''
	}

	public setItem(key: string, value: string) {
		if (!cookieWriteEnabled) {
			return
		}
		const expires = new Date()
		expires.setTime(expires.getTime() + SESSION_PUSH_THRESHOLD)
		Cookies.set(key, value, { expires })
	}

	public removeItem(key: string) {
		if (!cookieWriteEnabled) {
			return
		}
		Cookies.remove(key)
	}
}

export const globalStorage = new Storage()
export const cookieStorage = new CookieStorage()

export const getPersistentStorage = () => {
	let storage:
		| Storage
		| typeof window.localStorage
		| typeof window.sessionStorage
		| null = null

	if (typeof window === 'undefined') {
		return globalStorage
	}

	try {
		switch (mode) {
			case 'localStorage':
				storage = window.localStorage
				break
			case 'sessionStorage':
				storage = window.sessionStorage
				break
		}
		if (!storage) {
			internalLogOnce(
				'storage',
				'getPersistentStorage',
				'debug',
				`persistent storage was not found for mode ${mode}; using global storage`,
			)
			storage = globalStorage
		}
	} catch (e) {
		internalLogOnce(
			'storage',
			'getPersistentStorage',
			'debug',
			`failed to get persistent storage; using global storage`,
			e,
		)
		storage = globalStorage
	}
	return storage
}

export const setStorageMode = (m: Mode) => {
	mode = m
}

export const setCookieWriteEnabled = (enabled: boolean) => {
	cookieWriteEnabled = enabled
}

export const getItem = (key: string) => {
	try {
		return getPersistentStorage().getItem(key)
	} catch (e) {
		internalLogOnce(
			`storage`,
			'getItem',
			'debug',
			`failed to get item ${key}; using global storage`,
			e,
		)
		return globalStorage.getItem(key)
	}
}

export const setItem = (key: string, value: string) => {
	cookieStorage.setItem(key, value)
	try {
		return getPersistentStorage().setItem(key, value)
	} catch (e) {
		internalLogOnce(
			`storage`,
			'setItem',
			'debug',
			`failed to set item ${key}; using global storage`,
			e,
		)
		return globalStorage.setItem(key, value)
	}
}

export const removeItem = (key: string) => {
	cookieStorage.removeItem(key)
	try {
		return getPersistentStorage().removeItem(key)
	} catch (e) {
		internalLogOnce(
			`storage`,
			'removeItem',
			'debug',
			`failed to remove item ${key}; using global storage`,
			e,
		)
		return globalStorage.removeItem(key)
	}
}

export const monkeyPatchLocalStorage = (
	onSetItemHandler: ({
		keyName,
		keyValue,
	}: {
		keyName: string
		keyValue: string
	}) => void,
) => {
	if (mode === 'sessionStorage') {
		console.warn(
			`highlight.io cannot use local storage; segment integration will not work`,
		)
		return
	}
	if (typeof window === 'undefined') {
		console.warn(
			`highlight.io cannot use local storage (not a window); segment integration will not work`,
		)
		return
	}

	const originalSetItem = window.localStorage.setItem
	window.localStorage.setItem = function () {
		const [keyName, keyValue] = arguments as unknown as [
			key: string,
			value: string,
		]
		onSetItemHandler({ keyName, keyValue })
		originalSetItem.apply(this, [keyName, keyValue])
	}
}
