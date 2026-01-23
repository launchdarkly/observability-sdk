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
