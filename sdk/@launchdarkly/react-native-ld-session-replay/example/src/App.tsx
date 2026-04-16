import { SafeAreaView } from 'react-native'
import {
  ReactNativeLDClient,
  LDProvider,
  AutoEnvAttributes,
} from '@launchdarkly/react-native-client-sdk'
import { useEffect } from 'react'
import { createSessionReplayPlugin } from '@launchdarkly/session-replay-react-native'
import DialogsScreen from './DialogsScreen'

const plugin = createSessionReplayPlugin({
  isEnabled: true,
  maskTextInputs: true,
  maskWebViews: true,
  maskLabels: true,
  maskImages: true,
  maskAccessibilityIdentifiers: ['password', 'ssn'],
  minimumAlpha: 0.05,
})

// Replace with your LaunchDarkly mobile key.
// You can also set the LAUNCHDARKLY_MOBILE_KEY environment variable.
const MOBILE_KEY =
  process.env.LAUNCHDARKLY_MOBILE_KEY || 'YOUR_LAUNCHDARKLY_MOBILE_KEY_HERE'

const client = new ReactNativeLDClient(MOBILE_KEY, AutoEnvAttributes.Enabled, {
  plugins: [plugin],
})
const context = { kind: 'user', key: 'user-key-123abc' }

export default function App() {
  useEffect(() => {
    client.identify(context).catch((e: unknown) => console.log(e))
  }, [])

  return (
    <LDProvider client={client}>
      <SafeAreaView style={{ flex: 1, backgroundColor: '#000' }}>
        <DialogsScreen />
      </SafeAreaView>
    </LDProvider>
  )
}
