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

// rrweb masks all text under `strict`, only PII-like text under `default`, and no text under `none`
export const determineTextPrivacySetting = (
	privacyPolicy: PrivacySettingOption,
	maskPiiText?: boolean,
): PrivacySettingOption => {
	if (privacyPolicy === 'strict') {
		return 'strict'
	}
	const shouldMaskPiiText = maskPiiText ?? privacyPolicy === 'default'
	return shouldMaskPiiText ? 'default' : 'none'
}
