import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// Flutter port of the Android sample's
/// `e2e/android/.../smoothie/SmoothieListActivity.kt` +
/// `SmoothieAdapter.kt`. Renders a scrolling list of smoothies (image +
/// title) loaded from bundled assets, to exercise Session Replay against
/// image-heavy content.
class SmoothieListPage extends StatelessWidget {
  const SmoothieListPage({super.key});

  static const List<SmoothieItem> _smoothies = <SmoothieItem>[
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Smoothies')),
      body: ListView.builder(
        padding: const EdgeInsets.all(8),
        itemCount: _smoothies.length,
        itemBuilder: (context, index) {
          final item = _smoothies[index];
          return _SmoothieRow(item: item);
        },
      ),
    );
  }
}

/// Mirrors `SmoothieItem` from the Android sample.
class SmoothieItem {
  const SmoothieItem(this.title, this.imageFileName);

  final String title;
  final String imageFileName;
}

/// Mirrors the Android `item_smoothie.xml` row: a 64dp image followed by the
/// title. The image is loaded from the bundled asset, falling back to the app
/// icon if it can't be decoded.
class _SmoothieRow extends StatelessWidget {
  const _SmoothieRow({required this.item});

  final SmoothieItem item;

  @override
  Widget build(BuildContext context) {
    // The Android `SmoothieAdapter` keeps `imageView.ldMask()` commented out;
    // mirror that here. Wrap the image in `LDMask` to redact smoothie photos
    // from session replay:
    //   image = LDMask(child: image);
    final Widget image = Image.asset(
      'assets/smoothie/images/${item.imageFileName}',
      width: 64,
      height: 64,
      fit: BoxFit.cover,
      errorBuilder: (context, error, stackTrace) =>
          const Icon(Icons.local_drink, size: 64),
    );

    return Padding(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: image,
          ),
          const SizedBox(width: 12),
          Expanded(
            // Redact the smoothie title from session replay. `maskLabels` is
            // off globally (see main.dart), so wrap each label in `LDMask` to
            // mask it explicitly.
            child: LDMask(
              child: Text(
                item.title,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
