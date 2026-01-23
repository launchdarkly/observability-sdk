import { MaskInputOptions } from 'rrweb-snapshot'
import { PrivacySettingOption } from '../types/types'

// returns (1) whether all inputs should be masked and (2) which inputs should be masked
export const determineMaskInputOptions = (
	privacyPolicy: PrivacySettingOption,
	maskAllInputs?: boolean,
	maskInputOptions?: Partial<{
		color: boolean
		date: boolean
		'datetime-local': boolean
		email: boolean
		month: boolean
		number: boolean
		range: boolean
		search: boolean
		tel: boolean
		text: boolean
		time: boolean
		url: boolean
		week: boolean
		textarea: boolean
		select: boolean
		password: boolean
	}>,
): [maskAllOptions: boolean, maskOptions?: MaskInputOptions] => {
	switch (privacyPolicy) {
		case 'strict':
			return [true, undefined]
		case 'default':
			return [true, undefined]
		case 'none': {
			return [!!maskAllInputs, { ...maskInputOptions, password: true }]
		}
	}
}
