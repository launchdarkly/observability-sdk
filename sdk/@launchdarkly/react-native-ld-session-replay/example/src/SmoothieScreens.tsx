import {
  Alert,
  FlatList,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  type ImageSourcePropType,
} from 'react-native';
import {
  createNavigationContainerRef,
  DarkTheme,
  NavigationContainer,
} from '@react-navigation/native';
import {
  createNativeStackNavigator,
  type NativeStackScreenProps,
} from '@react-navigation/native-stack';
import {
  createNavigationObserver,
  LDClick,
  LDMask,
  LDUnmask,
} from '@launchdarkly/session-replay-react-native';

/**
 * React Native port of the Flutter example's `smoothie_list_page.dart`: an image-heavy list
 * (list → product → purchase) on a real React Navigation native stack, for exercising automatic
 * screen views, click ids and Session Replay image capture against the same UI on both SDKs.
 *
 * Route names match the Flutter MaterialApp build (`/smoothie-list`, `/smoothie-detail`,
 * `/smoothie-purchase`) and deliberately omit the smoothie id, so every product groups under one
 * screen and the two platforms report identical screen names.
 */
type SmoothieStackParamList = {
  '/smoothie-list': undefined;
  '/smoothie-detail': { id: string };
  '/smoothie-purchase': { id: string };
};

type SmoothieItem = {
  id: string;
  title: string;
  image: ImageSourcePropType;
};

const smoothieCatalog: SmoothieItem[] = [
  { id: 'berry-blue', title: 'Berry Blue', image: require('../assets/smoothie/images/berry-blue.jpg') },
  { id: 'carrot-chops', title: 'Carrot Chops', image: require('../assets/smoothie/images/carrot-chops.jpg') },
  { id: 'hulking-lemonade', title: 'Hulking Lemonade', image: require('../assets/smoothie/images/hulking-lemonade.jpg') },
  { id: 'kiwi-cutie', title: 'Kiwi Cutie', image: require('../assets/smoothie/images/kiwi-cutie.jpg') },
  { id: 'lemonberry', title: 'Lemonberry', image: require('../assets/smoothie/images/lemonberry.jpg') },
  { id: 'love-you-berry-much', title: 'Love You Berry Much', image: require('../assets/smoothie/images/love-you-berry-much.jpg') },
  { id: 'mango-jambo', title: 'Mango Jambo', image: require('../assets/smoothie/images/mango-jambo.jpg') },
  { id: 'one-in-a-melon', title: 'One in a Melon', image: require('../assets/smoothie/images/one-in-a-melon.jpg') },
  { id: 'papas-papaya', title: "Papa's Papaya", image: require('../assets/smoothie/images/papas-papaya.jpg') },
  { id: 'peanut-butter-cup', title: 'Peanut Butter Cup', image: require('../assets/smoothie/images/peanut-butter-cup.jpg') },
  { id: 'pina-y-coco', title: 'Piña y Coco', image: require('../assets/smoothie/images/pina-y-coco.jpg') },
  { id: 'sailor-man', title: 'Sailor Man', image: require('../assets/smoothie/images/sailor-man.jpg') },
  { id: 'thats-a-smore', title: "That's a S'more", image: require('../assets/smoothie/images/thats-a-smore.jpg') },
  { id: 'thats-berry-bananas', title: "That's Berry Bananas", image: require('../assets/smoothie/images/thats-berry-bananas.jpg') },
  { id: 'tropical-blue', title: 'Tropical Blue', image: require('../assets/smoothie/images/tropical-blue.jpg') },
];

function smoothieById(id: string): SmoothieItem {
  // Falls back to the first smoothie, as the Flutter GetX build does for an unknown id.
  return smoothieCatalog.find((item) => item.id === id) ?? smoothieCatalog[0]!;
}

const Stack = createNativeStackNavigator<SmoothieStackParamList>();
const navigationRef = createNavigationContainerRef<SmoothieStackParamList>();
const navigationObserver = createNavigationObserver(navigationRef, {
  category: 'navigation',
});
export default function SmoothieScreens() {
  return (
    <NavigationContainer
      ref={navigationRef}
      theme={DarkTheme}
      {...navigationObserver}
    >
      <Stack.Navigator initialRouteName="/smoothie-list">
        <Stack.Screen
          name="/smoothie-list"
          component={SmoothieListScreen}
          options={{ title: 'Smoothies' }}
        />
        <Stack.Screen
          name="/smoothie-detail"
          component={SmoothieDetailScreen}
          options={({ route }) => ({ title: smoothieById(route.params.id).title })}
        />
        <Stack.Screen
          name="/smoothie-purchase"
          component={PurchaseScreen}
          options={{ title: 'Purchase' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

/** Level one: the catalog. Each row's `LDClick` id becomes the click's `event.id`. */
function SmoothieListScreen({
  navigation,
}: NativeStackScreenProps<SmoothieStackParamList, '/smoothie-list'>) {
  return (
    <FlatList
      data={smoothieCatalog}
      keyExtractor={(item) => item.id}
      contentContainerStyle={styles.listContent}
      renderItem={({ item }) => (
        <LDClick id={`smoothie.${item.id}`}>
          <TouchableOpacity
            style={styles.row}
            onPress={() =>
              navigation.navigate('/smoothie-detail', { id: item.id })
            }
          >
            {/* The Flutter and Android samples leave image masking off here; wrap the image
                in <LDMask> to redact smoothie photos from session replay. */}
            <Image source={item.image} style={styles.rowImage} />
            {/* maskLabels is off globally, so the title is masked explicitly. */}
            <LDMask style={styles.rowTitle}>
              <Text style={styles.rowTitleText}>{item.title}</Text>
            </LDMask>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
        </LDClick>
      )}
    />
  );
}

/** Level two: one product with a full-size image and the purchase button. */
function SmoothieDetailScreen({
  navigation,
  route,
}: NativeStackScreenProps<SmoothieStackParamList, '/smoothie-detail'>) {
  const item = smoothieById(route.params.id);
  // The whole product page is exempt from global masking, so the photo and its copy are visible
  // in the recording. An explicit <LDMask> inside would still win.
  return (
    <LDUnmask style={styles.fill}>
      <ScrollView contentContainerStyle={styles.detailContent}>
        <Image source={item.image} style={styles.detailImage} />
        <Text style={styles.headline}>{item.title}</Text>
        <Text style={styles.body}>
          Fresh fruit, blended to order. This large image makes replay capture
          and deep view-path behavior easy to inspect.
        </Text>
        <LDClick id={`purchase.${item.id}`}>
          <TouchableOpacity
            style={styles.button}
            onPress={() =>
              navigation.navigate('/smoothie-purchase', { id: item.id })
            }
          >
            <Text style={styles.buttonText}>Purchase {item.title}</Text>
          </TouchableOpacity>
        </LDClick>
      </ScrollView>
    </LDUnmask>
  );
}

/** Level three: the deepest screen in the flow. */
function PurchaseScreen({
  route,
}: NativeStackScreenProps<SmoothieStackParamList, '/smoothie-purchase'>) {
  const item = smoothieById(route.params.id);
  return (
    <View style={styles.purchaseContent}>
      <Image source={item.image} style={styles.purchaseImage} />
      <Text style={styles.title}>{item.title}</Text>
      <Text style={styles.body}>$7.50</Text>
      <View style={styles.fill} />
      <LDClick id="purchase.confirm">
        <TouchableOpacity
          style={styles.button}
          onPress={() => Alert.alert(`${item.title} is being prepared.`)}
        >
          <Text style={styles.buttonText}>Confirm purchase</Text>
        </TouchableOpacity>
      </LDClick>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
  },
  listContent: {
    padding: 8,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  rowImage: {
    width: 64,
    height: 64,
    borderRadius: 4,
  },
  rowTitle: {
    flex: 1,
    marginLeft: 16,
  },
  rowTitleText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '500',
  },
  chevron: {
    color: '#888',
    fontSize: 24,
  },
  detailContent: {
    padding: 20,
  },
  detailImage: {
    width: '100%',
    height: 420,
    borderRadius: 24,
  },
  headline: {
    color: '#fff',
    fontSize: 28,
    fontWeight: '600',
    marginTop: 20,
  },
  title: {
    color: '#fff',
    fontSize: 22,
    fontWeight: '600',
    marginTop: 20,
  },
  body: {
    color: '#ccc',
    fontSize: 15,
    marginTop: 8,
  },
  button: {
    backgroundColor: '#6650A4',
    borderRadius: 24,
    paddingVertical: 14,
    paddingHorizontal: 20,
    alignItems: 'center',
    marginTop: 24,
  },
  buttonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '700',
  },
  purchaseContent: {
    flex: 1,
    padding: 20,
  },
  purchaseImage: {
    width: '100%',
    height: 240,
    borderRadius: 16,
  },
});
