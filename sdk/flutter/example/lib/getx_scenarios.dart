import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'smoothie_list_page.dart';

/// The GetX build's screens are the same three the MaterialApp build uses; only
/// the navigation differs. Each one is a named `GetPage` (see `getx_app.dart`)
/// reached with `Get.toNamed`, where the MaterialApp build pushes anonymous
/// `MaterialPageRoute`s — so the click and screen view signals can be compared
/// on identical UI.

/// Entry into the flow from the example's home page, which stays the root of the
/// GetX build so both modes start from the same screen.
class GetXSmoothieEntry extends StatelessWidget {
  const GetXSmoothieEntry({super.key});

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: () => Get.toNamed('/smoothies'),
      child: const Text('Smoothie Menu (GetX routes)'),
    );
  }
}

/// Level one at `/smoothies`: the shared catalog, selecting through GetX.
class GetXSmoothieMenuView extends StatelessWidget {
  const GetXSmoothieMenuView({super.key});

  @override
  Widget build(BuildContext context) {
    return SmoothieListPage(
      onSelect: (context, item) => Get.toNamed('/smoothies/${item.id}'),
    );
  }
}

/// Level two at `/smoothies/:id`.
class GetXSmoothieView extends StatelessWidget {
  const GetXSmoothieView({super.key});

  @override
  Widget build(BuildContext context) {
    final item = _selectedSmoothie();
    return SmoothieView(
      item: item,
      onPurchase: () => Get.toNamed('/smoothies/${item.id}/purchase'),
    );
  }
}

/// Level three at `/smoothies/:id/purchase`.
class GetXPurchaseView extends StatelessWidget {
  const GetXPurchaseView({super.key});

  @override
  Widget build(BuildContext context) => PurchaseView(item: _selectedSmoothie());
}

/// Resolves the `:id` path parameter, falling back to the first smoothie so a
/// hand-typed or deep-linked URL still renders something.
SmoothieItem _selectedSmoothie() {
  final id = Get.parameters['id'];
  return smoothieCatalog.firstWhere(
    (item) => item.id == id,
    orElse: () => smoothieCatalog.first,
  );
}
