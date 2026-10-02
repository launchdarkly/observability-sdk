import { useRef, useState } from 'react';
import {
  Animated,
  Image,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import WebView from 'react-native-webview';
import { LDMask, LDUnmask } from '@launchdarkly/session-replay-react-native';

/**
 * Manual test screen for `maskTestIDs` / `unmaskTestIDs` and the `<LDMask>` / `<LDUnmask>`
 * wrappers. The plugin in `App.tsx` is configured with `maskTestIDs: ['password', 'ssn']` and
 * `unmaskTestIDs: ['safe']`. Each row's testID (or wrapper) is picked to exercise a specific
 * case; the inline comment on each row explains the expected behavior under whatever values of
 * `maskLabels`, `maskImages`, `maskTextInputs`, and `maskWebViews` are currently set in the
 * plugin config.
 *
 * Section headers use `testID="safe"` so they remain readable in the recording regardless of
 * `maskLabels`.
 */
export default function MaskingScreen() {
  const [overlayVisible, setOverlayVisible] = useState(false);
  const [windowVisible, setWindowVisible] = useState(false);
  const overlayOpacity = useRef(new Animated.Value(0)).current;
  const windowOpacity = useRef(new Animated.Value(0)).current;

  const fadeIn = (
    opacity: Animated.Value,
    setVisible: (visible: boolean) => void
  ) => {
    opacity.setValue(0);
    setVisible(true);
    Animated.timing(opacity, {
      toValue: 1,
      duration: FADE_DURATION_MS,
      useNativeDriver: true,
    }).start();
  };

  const fadeOut = (
    opacity: Animated.Value,
    setVisible: (visible: boolean) => void
  ) => {
    Animated.timing(opacity, {
      toValue: 0,
      duration: FADE_DURATION_MS,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) setVisible(false);
    });
  };

  return (
    <View style={styles.root}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text testID="safe" style={styles.intro}>
          Some rows below will be masked in replays, depending on the plugin's
          config.
        </Text>

        <Text testID="safe" style={styles.sectionHeader}>
          Fading window over masked content
        </Text>

        {/* Fades an opaque full-screen panel in over the rows below across
            FADE_DURATION_MS. The rows show through while the alpha ramps — the
            replay should keep drawing their masks the whole way — and are
            fully covered once the fade lands, so the masks should disappear
            only at the end. The view-tree variant stays in the same native
            window; the Modal variant creates a second one. */}
        <View style={styles.buttonRow}>
          <TouchableOpacity
            testID="safe"
            style={styles.button}
            onPress={() => fadeIn(overlayOpacity, setOverlayVisible)}
          >
            <Text testID="safe" style={styles.buttonText}>
              Fade in overlay (view tree)
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            testID="safe"
            style={styles.button}
            onPress={() => fadeIn(windowOpacity, setWindowVisible)}
          >
            <Text testID="safe" style={styles.buttonText}>
              Fade in window (Modal)
            </Text>
          </TouchableOpacity>
        </View>

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
          Text input
        </Text>

        {/* Masked iff maskTextInputs is on. */}
        <TextInput
          testID="other"
          style={styles.textInput}
          defaultValue="text input contents"
        />

        <Text testID="safe" style={styles.sectionHeader}>
          WebView
        </Text>

        {/* Masked iff maskWebViews is on. */}
        <WebView
          testID="other"
          style={styles.webView}
          source={{ html: WEBVIEW_HTML }}
        />

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

      {/* View-tree overlay: same native window as the rows underneath. */}
      {overlayVisible && (
        <Animated.View
          style={[styles.overlayFill, { opacity: overlayOpacity }]}
        >
          <TomSawyerPanel
            title="Fading overlay (view tree)"
            onDismiss={() => fadeOut(overlayOpacity, setOverlayVisible)}
          />
        </Animated.View>
      )}

      {/* Modal overlay: a second native window fading in over the first.
          animationType="none" so the only animation is the slow one below. */}
      <Modal
        visible={windowVisible}
        transparent
        animationType="none"
        onRequestClose={() => fadeOut(windowOpacity, setWindowVisible)}
      >
        <Animated.View style={[styles.overlayFill, { opacity: windowOpacity }]}>
          <TomSawyerPanel
            title="Fading window (Modal)"
            onDismiss={() => fadeOut(windowOpacity, setWindowVisible)}
          />
        </Animated.View>
      </Modal>
    </View>
  );
}

/**
 * Full-screen panel of public-domain prose. Everything here is `testID="safe"`
 * so the panel itself is never masked — whatever gets masked in the replay
 * while it fades is coming from the rows behind it.
 */
function TomSawyerPanel({
  title,
  onDismiss,
}: {
  title: string;
  onDismiss: () => void;
}) {
  return (
    <View style={styles.panel}>
      <Text testID="safe" style={styles.panelTitle}>
        {title}
      </Text>
      <ScrollView contentContainerStyle={styles.panelBody}>
        {TOM_SAWYER_PARAGRAPHS.map((paragraph) => (
          <Text
            testID="safe"
            key={paragraph.slice(0, 24)}
            style={styles.panelText}
          >
            {paragraph}
          </Text>
        ))}
      </ScrollView>
      <TouchableOpacity testID="safe" style={styles.button} onPress={onDismiss}>
        <Text testID="safe" style={styles.buttonText}>
          Fade out
        </Text>
      </TouchableOpacity>
    </View>
  );
}

/**
 * Long enough to watch the alpha ramp cross the plugin's `minimumAlpha`
 * threshold and settle.
 */
const FADE_DURATION_MS = 5000;

/** The Adventures of Tom Sawyer, chapter II (public domain). */
const TOM_SAWYER_PARAGRAPHS = [
  'Saturday morning was come, and all the summer world was bright and fresh, and brimming with life. There was a song in every heart; and if the heart was young the music issued at the lips.',
  'Tom appeared on the sidewalk with a bucket of whitewash and a long-handled brush. He surveyed the fence, and all gladness left him and a deep melancholy settled down upon his spirit. Thirty yards of board fence nine feet high. Life to him seemed hollow, and existence but a burden.',
  'Sighing, he dipped his brush and passed it along the topmost plank; repeated the operation; did it again; compared the insignificant whitewashed streak with the far-reaching continent of unwhitewashed fence, and sat down on a tree-box discouraged.',
];

const LOGO = { uri: 'https://reactnative.dev/img/tiny_logo.png' };

const WEBVIEW_HTML = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
  </head>
  <body style="font-size: 32px; padding: 16px;">
    webview contents
  </body>
</html>`;

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
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
  textInput: {
    color: '#fff',
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#666',
    borderRadius: 4,
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  webView: {
    height: 120,
    backgroundColor: '#fff',
  },
  buttonRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  button: {
    backgroundColor: '#6650A4',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 16,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontWeight: '600',
  },
  overlayFill: {
    ...StyleSheet.absoluteFillObject,
  },
  // Fills the overlay edge to edge so it spans the full device width. The
  // background is fully opaque: the rows behind it show through only while the
  // container's alpha is ramping, and are hidden once the fade completes.
  panel: {
    flex: 1,
    width: '100%',
    backgroundColor: '#1C1B1F',
    padding: 20,
    gap: 12,
  },
  panelTitle: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  panelBody: {
    gap: 12,
  },
  panelText: {
    color: '#fff',
    fontSize: 15,
    lineHeight: 21,
  },
});
