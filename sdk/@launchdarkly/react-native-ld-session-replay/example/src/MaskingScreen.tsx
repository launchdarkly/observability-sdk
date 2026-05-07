import { Image, ScrollView, StyleSheet, Text, View } from 'react-native';
import { LDMask, LDUnmask } from '@launchdarkly/session-replay-react-native';

/**
 * Manual test screen for `maskTestIDs` / `unmaskTestIDs` and the `<LDMask>` / `<LDUnmask>`
 * wrappers. The plugin in `App.tsx` is configured with `maskTestIDs: ['password', 'ssn']` and
 * `unmaskTestIDs: ['safe']`. Each row's testID (or wrapper) is picked to exercise a specific
 * case; the inline comment on each row explains the expected behavior under whatever values of
 * `maskLabels` / `maskImages` are currently set in the plugin config.
 *
 * Section headers use `testID="safe"` so they remain readable in the recording regardless of
 * `maskLabels`.
 */
export default function MaskingScreen() {
  return (
    <ScrollView contentContainerStyle={styles.scroll}>
      <Text testID="safe" style={styles.intro}>
        Some rows below will be masked in replays, depending on the plugin's
        config.
      </Text>

      <Text testID="safe" style={styles.sectionHeader}>
        Text rows
      </Text>

      {/* Always masked: testID is in maskTestIDs (explicit-mask wins regardless of
          maskLabels). */}
      <Text testID="password" style={styles.row}>
        my password is hunter2
      </Text>

      {/* Always masked: testID is in maskTestIDs. */}
      <Text testID="ssn" style={styles.row}>
        ssn: 123-45-6789
      </Text>

      {/* Always unmasked: testID is in unmaskTestIDs (explicit-unmask overrides
          maskLabels). */}
      <Text testID="safe" style={styles.row}>
        safe text — should always be visible
      </Text>

      {/* Masked iff maskLabels is on. testID does not match any list — falls through to
          the global maskLabels rule. */}
      <Text testID="other" style={styles.row}>
        plain text with non-matching testID
      </Text>

      <Text testID="safe" style={styles.sectionHeader}>
        Image rows
      </Text>

      {/* Always masked: testID is in maskTestIDs (explicit-mask wins regardless of
          maskImages). */}
      <View style={styles.imageRow}>
        <Image testID="password" source={LOGO} style={styles.image} />
        <Text testID="safe" style={styles.imageLabel}>
          testID="password"
        </Text>
      </View>

      {/* Always unmasked: testID is in unmaskTestIDs (explicit-unmask overrides
          maskImages). */}
      <View style={styles.imageRow}>
        <Image testID="safe" source={LOGO} style={styles.image} />
        <Text testID="safe" style={styles.imageLabel}>
          testID="safe"
        </Text>
      </View>

      {/* Masked iff maskImages is on. testID does not match any list — falls through to
          the global maskImages rule. */}
      <View style={styles.imageRow}>
        <Image testID="other" source={LOGO} style={styles.image} />
        <Text testID="safe" style={styles.imageLabel}>
          testID="other"
        </Text>
      </View>

      <Text testID="safe" style={styles.sectionHeader}>
        LDMask / LDUnmask
      </Text>

      {/* Always masked: <LDMask> applies explicit-mask to its subtree. */}
      <LDMask>
        <Text style={styles.row}>LDMask wrapping Text — always masked</Text>
      </LDMask>

      {/* Always unmasked: <LDUnmask> overrides maskLabels for its subtree. */}
      <LDUnmask>
        <Text style={styles.row}>
          LDUnmask wrapping Text — always visible (overrides maskLabels)
        </Text>
      </LDUnmask>

      {/* Masked: ancestor LDMask wins over descendant LDUnmask. */}
      <LDMask>
        <LDUnmask>
          <Text style={styles.row}>
            LDMask &gt; LDUnmask &gt; Text — masked (mask wins over unmask)
          </Text>
        </LDUnmask>
      </LDMask>
    </ScrollView>
  );
}

const LOGO = { uri: 'https://reactnative.dev/img/tiny_logo.png' };

const styles = StyleSheet.create({
  scroll: {
    padding: 16,
    gap: 12,
  },
  intro: {
    color: '#CAC4D0',
    fontSize: 14,
    fontStyle: 'italic',
  },
  sectionHeader: {
    color: '#fff',
    fontSize: 20,
    fontWeight: 'bold',
    marginTop: 8,
  },
  row: {
    color: '#fff',
    fontSize: 16,
    paddingVertical: 6,
  },
  imageRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  image: {
    width: 64,
    height: 64,
  },
  imageLabel: {
    color: '#fff',
    fontSize: 16,
  },
});
