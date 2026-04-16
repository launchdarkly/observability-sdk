import React, { useEffect, useRef, useState } from 'react'
import {
  ActionSheetIOS,
  Alert,
  Animated,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function SectionHeader({ title, topSpacing }: { title: string; topSpacing?: boolean }) {
  return (
    <>
      <Text style={[styles.sectionTitle, topSpacing ? { marginTop: 16 } : undefined]}>
        {title}
      </Text>
      <View style={styles.divider} />
    </>
  )
}

function Btn({
  label,
  onPress,
  variant,
}: {
  label: string
  onPress: () => void
  variant?: 'default' | 'danger' | 'accent'
}) {
  const extra =
    variant === 'danger'
      ? styles.btnDanger
      : variant === 'accent'
        ? styles.btnAccent
        : undefined
  return (
    <TouchableOpacity style={[styles.btn, extra]} onPress={onPress} activeOpacity={0.75}>
      <Text style={styles.btnText}>{label}</Text>
    </TouchableOpacity>
  )
}

function SheetContent({
  title,
  body,
  onClose,
}: {
  title: string
  body: string
  onClose: () => void
}) {
  return (
    <>
      <View style={styles.handle} />
      <Text style={styles.sheetTitle}>{title}</Text>
      <Text style={styles.sheetBody}>{body}</Text>
      <Btn label="Option A" onPress={() => {}} />
      <Btn label="Option B" onPress={() => {}} />
      <Btn label="Close" onPress={onClose} variant="danger" />
    </>
  )
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

export default function DialogsScreen() {
  const [delayText, setDelayText] = useState('0')

  // -- Slide-up sheet (in the normal view tree) --
  const [slideUpVisible, setSlideUpVisible] = useState(false)
  const slideUpY = useRef(new Animated.Value(400)).current

  // -- Centered popup card (in the normal view tree) --
  const [popupCardVisible, setPopupCardVisible] = useState(false)
  const cardScale = useRef(new Animated.Value(0.8)).current
  const cardOpacity = useRef(new Animated.Value(0)).current
  const [countdown, setCountdown] = useState(8)
  const countdownTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  // -- Modal overlay sheet (built-in RN Modal, animationType="slide") --
  const [modalSheetVisible, setModalSheetVisible] = useState(false)

  // -- Modal + manual slide-up sheet --
  const [modalSlideVisible, setModalSlideVisible] = useState(false)
  const modalSlideY = useRef(new Animated.Value(400)).current

  // -- Prompt modal (Android fallback for Alert.prompt) --
  const [promptVisible, setPromptVisible] = useState(false)
  const [promptText, setPromptText] = useState('')

  // -- Tooltip --
  const tooltipOpacity = useRef(new Animated.Value(0)).current
  const [tooltipVisible, setTooltipVisible] = useState(false)

  // ---------------------------------------------------------------------------
  // Countdown timer helpers
  // ---------------------------------------------------------------------------
  const clearCountdownTimer = () => {
    if (countdownTimer.current) {
      clearInterval(countdownTimer.current)
      countdownTimer.current = null
    }
  }

  useEffect(() => () => clearCountdownTimer(), [])

  // ---------------------------------------------------------------------------
  // Delay helper
  // ---------------------------------------------------------------------------
  const waitForDelay = () =>
    new Promise<void>((resolve) => {
      const secs = parseFloat(delayText)
      if (secs > 0) setTimeout(resolve, secs * 1000)
      else resolve()
    })

  // ---------------------------------------------------------------------------
  // Alerts
  // ---------------------------------------------------------------------------

  const onSimpleAlert = async () => {
    await waitForDelay()
    Alert.alert('Simple Alert', 'This is a simple alert dialog.', [{ text: 'OK' }])
  }

  const onAcceptCancelAlert = async () => {
    await waitForDelay()
    Alert.alert('Question', 'Do you want to proceed?', [
      { text: 'No', style: 'cancel' },
      {
        text: 'Yes',
        onPress: () => Alert.alert('Result', 'You chose: Yes', [{ text: 'OK' }]),
      },
    ])
  }

  const onPrompt = async () => {
    await waitForDelay()
    if (Platform.OS === 'ios') {
      Alert.prompt(
        'Prompt',
        'Enter your name:',
        (result) => {
          if (result != null) {
            Alert.alert('Prompt Result', `You entered: ${result}`, [{ text: 'OK' }])
          }
        },
        'plain-text',
        '',
        'default',
      )
    } else {
      setPromptText('')
      setPromptVisible(true)
    }
  }

  // ---------------------------------------------------------------------------
  // Bottom Sheets / Overlays
  // ---------------------------------------------------------------------------

  const onActionSheet = async () => {
    await waitForDelay()
    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions(
        {
          title: 'Action Sheet: Choose an option',
          options: ['Cancel', 'Delete', 'Option A', 'Option B', 'Option C'],
          cancelButtonIndex: 0,
          destructiveButtonIndex: 1,
        },
        (index) => console.log(`Action Sheet selection: ${index}`),
      )
    } else {
      Alert.alert(
        'Action Sheet: Choose an option',
        undefined,
        [
          { text: 'Option A', onPress: () => console.log('Option A') },
          { text: 'Option B', onPress: () => console.log('Option B') },
          { text: 'Option C', onPress: () => console.log('Option C') },
          { text: 'Delete', style: 'destructive', onPress: () => console.log('Delete') },
          { text: 'Cancel', style: 'cancel' },
        ],
      )
    }
  }

  // --- Slide-up sheet (view tree) ---

  const showSlideUp = async () => {
    await waitForDelay()
    slideUpY.setValue(400)
    setSlideUpVisible(true)
    Animated.timing(slideUpY, {
      toValue: 0,
      duration: 300,
      useNativeDriver: true,
    }).start()
  }

  const dismissSlideUp = () => {
    Animated.timing(slideUpY, {
      toValue: 400,
      duration: 250,
      useNativeDriver: true,
    }).start(() => setSlideUpVisible(false))
  }

  // --- Centered popup card (view tree) ---

  const showPopupCard = async () => {
    await waitForDelay()
    const secs = parseInt(delayText, 10) > 0 ? parseInt(delayText, 10) : 8
    clearCountdownTimer()
    setCountdown(secs)
    cardScale.setValue(0.8)
    cardOpacity.setValue(0)
    setPopupCardVisible(true)
    Animated.parallel([
      Animated.timing(cardScale, { toValue: 1, duration: 250, useNativeDriver: true }),
      Animated.timing(cardOpacity, { toValue: 1, duration: 250, useNativeDriver: true }),
    ]).start()
    let remaining = secs
    countdownTimer.current = setInterval(() => {
      remaining -= 1
      setCountdown(remaining)
      if (remaining <= 0) dismissPopupCard()
    }, 1000)
  }

  const dismissPopupCard = () => {
    clearCountdownTimer()
    Animated.parallel([
      Animated.timing(cardScale, { toValue: 0.8, duration: 200, useNativeDriver: true }),
      Animated.timing(cardOpacity, { toValue: 0, duration: 200, useNativeDriver: true }),
    ]).start(() => setPopupCardVisible(false))
  }

  // --- Modal overlay sheet (animationType="slide") ---

  const onModalSheet = async () => {
    await waitForDelay()
    setModalSheetVisible(true)
  }

  // --- Modal + manual slide-up ---

  const onModalSlideSheet = async () => {
    await waitForDelay()
    modalSlideY.setValue(400)
    setModalSlideVisible(true)
    Animated.timing(modalSlideY, {
      toValue: 0,
      duration: 300,
      useNativeDriver: true,
    }).start()
  }

  const dismissModalSlide = () => {
    Animated.timing(modalSlideY, {
      toValue: 400,
      duration: 250,
      useNativeDriver: true,
    }).start(() => setModalSlideVisible(false))
  }

  // ---------------------------------------------------------------------------
  // Tooltip
  // ---------------------------------------------------------------------------

  const onTooltip = async () => {
    await waitForDelay()
    setTooltipVisible(true)
    tooltipOpacity.setValue(0)
    Animated.timing(tooltipOpacity, {
      toValue: 1,
      duration: 200,
      useNativeDriver: true,
    }).start()
    const delaySecs = parseFloat(delayText)
    const stay = delaySecs > 0 ? delaySecs * 1000 : 2000
    setTimeout(() => {
      Animated.timing(tooltipOpacity, {
        toValue: 0,
        duration: 200,
        useNativeDriver: true,
      }).start(() => setTooltipVisible(false))
    }, stay)
  }

  // ---------------------------------------------------------------------------
  // Countdown label
  // ---------------------------------------------------------------------------

  const pad = (n: number) => String(Math.floor(Math.max(n, 0))).padStart(2, '0')
  const countdownLabel = `${pad(countdown / 60)}:${pad(countdown % 60)}`

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <View style={styles.root}>
      {/* ----------------------------------------------------------------- */}
      {/* Scrollable button list                                             */}
      {/* ----------------------------------------------------------------- */}
      <ScrollView contentContainerStyle={styles.scroll}>
        {/* Delay config */}
        <View style={styles.delayRow}>
          <Text style={styles.label}>Show Delay (s):</Text>
          <TextInput
            style={styles.delayInput}
            value={delayText}
            onChangeText={setDelayText}
            keyboardType="numeric"
          />
        </View>

        {/* Alerts */}
        <SectionHeader title="Alerts" />
        <View style={styles.row}>
          <Btn label="Simple Alert" onPress={onSimpleAlert} />
          <Btn label="Accept / Cancel" onPress={onAcceptCancelAlert} />
          <Btn label="Prompt" onPress={onPrompt} />
        </View>

        {/* Bottom Sheets */}
        <SectionHeader title="Bottom Sheets" topSpacing />
        <View style={styles.col}>
          <Btn label="Action Sheet (native)" onPress={onActionSheet} />
          <Btn label="Slide-up Sheet (view tree)" onPress={showSlideUp} />
          <Btn label="Centered Popup Card" onPress={showPopupCard} />
          <Btn label="Modal Overlay Sheet" onPress={onModalSheet} />
          <Btn label="Modal + Slide-up Sheet" onPress={onModalSlideSheet} />
        </View>

        {/* Tooltip */}
        <SectionHeader title="Tooltip" topSpacing />
        <View style={styles.row}>
          <Btn label="Show Tooltip Popup" onPress={onTooltip} />
        </View>
      </ScrollView>

      {/* ----------------------------------------------------------------- */}
      {/* In-tree overlays (not inside any Modal)                           */}
      {/* ----------------------------------------------------------------- */}

      {/* Slide-up sheet */}
      {slideUpVisible && (
        <Pressable style={styles.dimBottom} onPress={dismissSlideUp}>
          <Animated.View style={[styles.sheet, { transform: [{ translateY: slideUpY }] }]}>
            <Pressable>
              <SheetContent
                title="Slide-up Sheet (View Tree)"
                body="Built from regular RN views with slide-up animation. Lives in the normal component tree — SR should capture this."
                onClose={dismissSlideUp}
              />
            </Pressable>
          </Animated.View>
        </Pressable>
      )}

      {/* Centered popup card */}
      {popupCardVisible && (
        <Pressable style={styles.dimCenter} onPress={dismissPopupCard}>
          <Animated.View
            style={[
              styles.card,
              { transform: [{ scale: cardScale }], opacity: cardOpacity },
            ]}
          >
            <Pressable>
              {/* Header */}
              <View style={styles.cardHeader}>
                <View>
                  <Text style={styles.cardTitle}>Testing...</Text>
                  <Text style={styles.cardSubtitle}>Sample subtitle</Text>
                </View>
                <TouchableOpacity onPress={dismissPopupCard} hitSlop={8}>
                  <Text style={styles.cardCloseText}>✕</Text>
                </TouchableOpacity>
              </View>
              {/* Timer */}
              <View style={styles.timerCircle}>
                <Text style={styles.timerLabel}>{countdownLabel}</Text>
                <Text style={styles.timerSub}>Time Remaining</Text>
              </View>
              {/* Actions */}
              <Btn label="Stop" onPress={dismissPopupCard} variant="accent" />
              <Text style={styles.cardFooter}>Device is unresponsive</Text>
            </Pressable>
          </Animated.View>
        </Pressable>
      )}

      {/* Tooltip */}
      {tooltipVisible && (
        <Animated.View
          pointerEvents="none"
          style={[styles.tooltip, { opacity: tooltipOpacity }]}
        >
          <Text style={styles.tooltipText}>This is a custom tooltip popup!</Text>
        </Animated.View>
      )}

      {/* ----------------------------------------------------------------- */}
      {/* Modal overlays                                                     */}
      {/* ----------------------------------------------------------------- */}

      {/* Modal overlay sheet — animationType="slide" handles the animation */}
      <Modal
        visible={modalSheetVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setModalSheetVisible(false)}
      >
        <Pressable style={styles.dimBottom} onPress={() => setModalSheetVisible(false)}>
          <Pressable style={styles.sheet}>
            <SheetContent
              title="Modal Overlay Sheet"
              body="Presented via React Native <Modal transparent animationType='slide'>. Tests whether SR captures content rendered in a Modal window."
              onClose={() => setModalSheetVisible(false)}
            />
          </Pressable>
        </Pressable>
      </Modal>

      {/* Modal + manual slide-up sheet */}
      <Modal
        visible={modalSlideVisible}
        transparent
        animationType="fade"
        onRequestClose={dismissModalSlide}
      >
        <Pressable style={styles.dimBottom} onPress={dismissModalSlide}>
          <Animated.View style={[styles.sheet, { transform: [{ translateY: modalSlideY }] }]}>
            <Pressable>
              <SheetContent
                title="Modal + Slide-up Sheet"
                body="A slide-up sheet inside a transparent <Modal animationType='fade'>. Tests SR capture when a Modal window and a custom slide animation are combined."
                onClose={dismissModalSlide}
              />
            </Pressable>
          </Animated.View>
        </Pressable>
      </Modal>

      {/* Prompt modal — Android only (iOS uses native Alert.prompt) */}
      {Platform.OS === 'android' && (
        <Modal
          visible={promptVisible}
          transparent
          animationType="fade"
          onRequestClose={() => setPromptVisible(false)}
        >
          <KeyboardAvoidingView style={styles.dimCenter} behavior="padding">
            <Pressable>
              <View style={styles.promptCard}>
                <Text style={styles.cardTitle}>Prompt</Text>
                <Text style={styles.sheetBody}>Enter your name:</Text>
                <TextInput
                  style={styles.promptInput}
                  value={promptText}
                  onChangeText={setPromptText}
                  placeholder="Name..."
                  placeholderTextColor="#888"
                  autoFocus
                />
                <View style={styles.promptButtons}>
                  <Btn
                    label="Cancel"
                    onPress={() => setPromptVisible(false)}
                  />
                  <Btn
                    label="OK"
                    variant="accent"
                    onPress={() => {
                      setPromptVisible(false)
                      Alert.alert('Prompt Result', `You entered: ${promptText}`, [{ text: 'OK' }])
                    }}
                  />
                </View>
              </View>
            </Pressable>
          </KeyboardAvoidingView>
        </Modal>
      )}
    </View>
  )
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const CARD_BG = '#1C1B1F'
const ACCENT = '#3F51B5'
const DANGER = '#F2B8B5'

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000',
  },
  scroll: {
    padding: 16,
    paddingBottom: 48,
  },

  // Delay row
  delayRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 16,
  },
  label: {
    color: '#fff',
  },
  delayInput: {
    color: '#fff',
    backgroundColor: '#333',
    borderRadius: 4,
    paddingHorizontal: 10,
    paddingVertical: 4,
    width: 60,
    textAlign: 'center',
  },

  // Section headers
  sectionTitle: {
    color: '#fff',
    fontSize: 22,
    fontWeight: 'bold',
  },
  divider: {
    height: 1,
    backgroundColor: '#555',
    marginTop: 4,
    marginBottom: 8,
  },

  // Button layouts
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  col: {
    gap: 8,
  },

  // Buttons
  btn: {
    backgroundColor: '#6650A4',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 16,
    alignItems: 'center',
  },
  btnText: {
    color: '#fff',
    fontWeight: '600',
  },
  btnDanger: {
    backgroundColor: DANGER,
  },
  btnAccent: {
    backgroundColor: ACCENT,
  },

  // Dim overlays
  dimBottom: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  dimCenter: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },

  // Sheet
  sheet: {
    backgroundColor: CARD_BG,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
    gap: 8,
  },
  handle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#888',
    alignSelf: 'center',
    marginBottom: 8,
  },
  sheetTitle: {
    color: '#fff',
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  sheetBody: {
    color: '#CAC4D0',
    marginBottom: 8,
  },

  // Popup card
  card: {
    width: 320,
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 24,
    gap: 12,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  cardTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1C1B1F',
  },
  cardSubtitle: {
    fontSize: 14,
    color: '#666',
  },
  cardCloseText: {
    fontSize: 18,
    color: '#666',
    padding: 4,
  },
  timerCircle: {
    width: 160,
    height: 160,
    borderRadius: 80,
    borderWidth: 3,
    borderColor: ACCENT,
    alignSelf: 'center',
    justifyContent: 'center',
    alignItems: 'center',
  },
  timerLabel: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#1C1B1F',
  },
  timerSub: {
    fontSize: 12,
    color: '#999',
  },
  cardFooter: {
    textAlign: 'center',
    fontSize: 13,
    color: '#666',
  },

  // Tooltip
  tooltip: {
    position: 'absolute',
    alignSelf: 'center',
    top: '50%',
    backgroundColor: '#333',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 16,
  },
  tooltipText: {
    color: '#fff',
    fontSize: 14,
  },

  // Prompt card (Android)
  promptCard: {
    width: 300,
    backgroundColor: CARD_BG,
    borderRadius: 12,
    padding: 24,
    gap: 12,
  },
  promptInput: {
    borderWidth: 1,
    borderColor: '#555',
    borderRadius: 6,
    padding: 10,
    color: '#fff',
  },
  promptButtons: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
  },
})
