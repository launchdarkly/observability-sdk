import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// Flutter port of the Android sample's
/// `e2e/android/.../smoothie/SmoothieListActivity.kt` +
/// `SmoothieAdapter.kt`: a scrolling list of smoothies (image + title) loaded
/// from bundled assets, to exercise Session Replay against image-heavy content.
///
/// Also the entry to a three-level drill-down — list, [SmoothieView],
/// [PurchaseView] — for checking how a deep screen hierarchy is reported. The
/// GetX build reuses these same three screens and only replaces how a selection
/// is pushed, so the two routing stacks can be compared on identical UI.
class SmoothieListPage extends StatelessWidget {
  const SmoothieListPage({super.key, this.onSelect});

  /// Opens the tapped smoothie. Defaults to a [Navigator] push; the GetX build
  /// passes `Get.toNamed` instead.
  final void Function(BuildContext context, SmoothieItem item)? onSelect;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Smoothies')),
      body: ListView.builder(
        padding: const EdgeInsets.all(8),
        itemCount: smoothieCatalog.length,
        itemBuilder: (context, index) {
          final item = smoothieCatalog[index];
          return _SmoothieRow(
            item: item,
            onTap: () => (onSelect ?? _pushSmoothie)(context, item),
          );
        },
      ),
    );
  }
}

/// Pushes the shared smoothie screens through [Navigator], the default for the
/// MaterialApp build.
///
/// Both routes are named without the smoothie id: the screen view should group
/// every product under one screen, not fan out into one per item.
void _pushSmoothie(BuildContext context, SmoothieItem item) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      settings: const RouteSettings(name: '/smoothie-detail'),
      builder: (context) => SmoothieView(
        item: item,
        onPurchase: () => Navigator.of(context).push(
          MaterialPageRoute<void>(
            settings: const RouteSettings(name: '/smoothie-purchase'),
            builder: (_) => PurchaseView(item: item),
          ),
        ),
      ),
    ),
  );
}

/// Mirrors `SmoothieItem` from the Android sample.
class SmoothieItem {
  const SmoothieItem(this.title, this.imageFileName);

  final String title;
  final String imageFileName;

  String get id => imageFileName.substring(0, imageFileName.lastIndexOf('.'));
}

/// Shared by the list and both detail screens, in either navigation mode.
const smoothieCatalog = <SmoothieItem>[
  SmoothieItem('Berry Blue', 'berry-blue.jpg'),
  SmoothieItem('Carrot Chops', 'carrot-chops.jpg'),
  SmoothieItem('Hulking Lemonade', 'hulking-lemonade.jpg'),
  SmoothieItem('Kiwi Cutie', 'kiwi-cutie.jpg'),
  SmoothieItem('Lemonberry', 'lemonberry.jpg'),
  SmoothieItem('Love You Berry Much', 'love-you-berry-much.jpg'),
  SmoothieItem('Mango Jambo', 'mango-jambo.jpg'),
  SmoothieItem('One in a Melon', 'one-in-a-melon.jpg'),
  SmoothieItem("Papa's Papaya", 'papas-papaya.jpg'),
  SmoothieItem('Peanut Butter Cup', 'peanut-butter-cup.jpg'),
  SmoothieItem('Piña y Coco', 'pina-y-coco.jpg'),
  SmoothieItem('Sailor Man', 'sailor-man.jpg'),
  SmoothieItem("That's a S'more", 'thats-a-smore.jpg'),
  SmoothieItem("That's Berry Bananas", 'thats-berry-bananas.jpg'),
  SmoothieItem('Tropical Blue', 'tropical-blue.jpg'),
];

String smoothieAsset(SmoothieItem item) =>
    'assets/smoothie/images/${item.imageFileName}';

/// Mirrors the Android `item_smoothie.xml` row: a 64dp image followed by the
/// title, now tappable. A row reports `ListTile`; its `ValueKey` becomes the
/// click's `event.id`.
class _SmoothieRow extends StatelessWidget {
  const _SmoothieRow({required this.item, required this.onTap});

  final SmoothieItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    // The Android `SmoothieAdapter` keeps `imageView.ldMask()` commented out;
    // mirror that here. Wrap the image in `LDMask` to redact smoothie photos
    // from session replay:
    //   image = LDMask(child: image);
    final Widget image = Image.asset(
      smoothieAsset(item),
      width: 64,
      height: 64,
      fit: BoxFit.cover,
      errorBuilder: (context, error, stackTrace) =>
          const Icon(Icons.local_drink, size: 64),
    );

    return ListTile(
      key: ValueKey('smoothie.${item.id}'),
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      leading: Hero(
        tag: item.id,
        child: ClipRRect(borderRadius: BorderRadius.circular(4), child: image),
      ),
      // Redact the smoothie title from session replay. `maskLabels` is off
      // globally (see main.dart), so wrap each label in `LDMask` to mask it
      // explicitly. Click text is governed separately by
      // `PrivacyOptions.maskClickText`, so the row still reports its label.
      title: LDMask(
        child: Text(item.title, style: Theme.of(context).textTheme.titleMedium),
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}

/// Level two: one product, with a full-size image and the purchase button.
class SmoothieView extends StatelessWidget {
  const SmoothieView({super.key, required this.item, required this.onPurchase});

  final SmoothieItem item;

  /// Opens the purchase screen, supplied by whichever navigation stack pushed
  /// this view.
  final VoidCallback onPurchase;

  @override
  Widget build(BuildContext context) {
    // The whole product page is exempt from the screen-wide masking configured
    // in main.dart (`maskImages`, `maskTextInputs`), so the full-size photo and
    // its copy are visible in the recording. LDUnmask only overrides global
    // policy: an explicit LDMask inside would still win.
    return LDUnmask(
      child: Scaffold(
        appBar: AppBar(title: Text(item.title)),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Hero(
              tag: item.id,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(24),
                child: Image.asset(
                  smoothieAsset(item),
                  height: 420,
                  fit: BoxFit.cover,
                ),
              ),
            ),
            const SizedBox(height: 20),
            Text(item.title, style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 8),
            const Text(
              'Fresh fruit, blended to order. This large image makes replay '
              'capture and deep widget-path behavior easy to inspect.',
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              key: ValueKey('purchase.${item.id}'),
              onPressed: onPurchase,
              icon: const Icon(Icons.shopping_bag_outlined),
              label: Text('Purchase ${item.title}'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Level three: the deepest screen in the flow, pushed from [SmoothieView].
class PurchaseView extends StatelessWidget {
  const PurchaseView({super.key, required this.item});

  final SmoothieItem item;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Purchase')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: Image.asset(
                smoothieAsset(item),
                height: 240,
                fit: BoxFit.cover,
              ),
            ),
            const SizedBox(height: 20),
            Text(item.title, style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 8),
            const Text(r'$7.50'),
            const Spacer(),
            FilledButton(
              key: const ValueKey('purchase.confirm'),
              onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('${item.title} is being prepared.')),
              ),
              child: const Text('Confirm purchase'),
            ),
          ],
        ),
      ),
    );
  }
}
