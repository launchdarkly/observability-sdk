import { PrivacySettingOption } from '../types/types'
import { MaskInputOptions } from '../types/record'

// returns (1) whether all inputs should be masked and (2) which inputs should be masked
export const determineMaskInputOptions = (
	privacyPolicy: PrivacySettingOption,
	maskAllInputs?: boolean,
	maskInputOptions?: MaskInputOptions,
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

// returns the privacy setting passed to rrweb, which only masks PII-like text under `default`
export const determineTextPrivacySetting = (
	privacyPolicy: PrivacySettingOption,
	maskPiiText?: boolean,
): PrivacySettingOption => {
	switch (privacyPolicy) {
		case 'strict':
			return 'strict'
		case 'default':
			return maskPiiText === false ? 'none' : 'default'
		case 'none':
			return maskPiiText === true ? 'default' : 'none'
	}
}
