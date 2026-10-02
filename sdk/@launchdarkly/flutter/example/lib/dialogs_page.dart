import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// Flutter port of `sdk/@launchdarkly/mobile-dotnet/sample/DialogsPage.xaml`
/// (+ `.xaml.cs`). A catalog of the dialog/overlay mechanisms Flutter offers,
/// used to exercise Session Replay against transient, animated, and modal UI.
///
/// Because `main.dart` wraps the whole app in `SessionReplayCapture`, the root
/// `Navigator`/`Overlay` lives inside the capture boundary — so every dialog,
/// sheet, snackbar, and menu below is Flutter-rendered and captured.
///
/// The "Show delay" field mirrors MAUI's `DelayEntry`: it delays presentation
/// and auto-dismisses transient items, so a recording can catch them mid-state.
class DialogsPage extends StatefulWidget {
  const DialogsPage({super.key});

  @override
  State<DialogsPage> createState() => _DialogsPageState();
}

class _DialogsPageState extends State<DialogsPage> {
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final TextEditingController _delayController = TextEditingController(
    text: '0',
  );
  final GlobalKey<TooltipState> _tooltipKey = GlobalKey<TooltipState>();

  @override
  void dispose() {
    _delayController.dispose();
    super.dispose();
  }

  Duration get _delay {
    final seconds = double.tryParse(_delayController.text.trim()) ?? 0;
    if (seconds <= 0) return Duration.zero;
    return Duration(milliseconds: (seconds * 1000).round());
  }

  /// Waits the configured "Show delay" before presenting, mirroring MAUI's
  /// `WaitForDelay`.
  Future<void> _waitDelay() => _delay == Duration.zero
      ? Future<void>.value()
      : Future<void>.delayed(_delay);

  /// Auto-dismiss interval for transient items (snackbar, banner, overlays).
  /// Falls back to [fallback] when no delay is configured.
  Duration _autoDismiss({Duration fallback = const Duration(seconds: 3)}) =>
      _delay == Duration.zero ? fallback : _delay;

  // --- Alerts ---

  Future<void> _onSimpleAlert() async {
    await _waitDelay();
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Simple Alert'),
        content: const Text('This is a simple alert dialog.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  Future<void> _onAcceptCancelAlert() async {
    await _waitDelay();
    if (!mounted) return;
    final answer = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Question'),
        content: const Text('Do you want to proceed?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('No'),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Yes'),
          ),
        ],
      ),
    );
    if (!mounted || answer == null) return;
    _showSnackBar('You chose: ${answer ? 'Yes' : 'No'}');
  }

  Future<void> _onPrompt() async {
    await _waitDelay();
    if (!mounted) return;
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Prompt'),
        // The field is masked by the global `maskTextInputs` policy. Wrapping
        // the live value in `LDMask` shows per-widget masking works inside an
        // overlay too.
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: controller,
              autofocus: true,
              decoration: const InputDecoration(hintText: 'Name...'),
            ),
            const SizedBox(height: 8),
            LDMask(
              child: ValueListenableBuilder<TextEditingValue>(
                valueListenable: controller,
                builder: (context, value, _) =>
                    Text('Live value: ${value.text}'),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(controller.text),
            child: const Text('OK'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (!mounted || result == null) return;
    _showSnackBar('You entered: $result');
  }

  Future<void> _onSimpleOptions() async {
    await _waitDelay();
    if (!mounted) return;
    final choice = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('Choose an option'),
        children: [
          for (final option in ['Option A', 'Option B', 'Option C'])
            SimpleDialogOption(
              onPressed: () => Navigator.of(ctx).pop(option),
              child: Text(option),
            ),
        ],
      ),
    );
    if (!mounted || choice == null) return;
    _showSnackBar('Selected: $choice');
  }

  Future<void> _onCupertinoAlert() async {
    await _waitDelay();
    if (!mounted) return;
    await showCupertinoDialog<void>(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Cupertino Alert'),
        content: const Text('An iOS-style alert rendered by Flutter.'),
        actions: [
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Delete'),
          ),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  // --- Bottom Sheets ---

  Future<void> _onModalSheet() async {
    await _waitDelay();
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => _sheetBody(
        ctx,
        title: 'Modal Bottom Sheet',
        description:
            'A standard modal sheet with a drag handle. Lives in the root '
            'Overlay, so Session Replay captures it.',
      ),
    );
  }

  Future<void> _onExpandableSheet() async {
    await _waitDelay();
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.5,
        minChildSize: 0.25,
        maxChildSize: 0.95,
        builder: (_, scrollController) => ListView.builder(
          controller: scrollController,
          itemCount: 30,
          itemBuilder: (_, i) => i == 0
              ? Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(
                    'Draggable Sheet',
                    style: Theme.of(ctx).textTheme.titleLarge,
                  ),
                )
              : ListTile(title: Text('Item $i')),
        ),
      ),
    );
  }

  Future<void> _onPersistentSheet() async {
    await _waitDelay();
    final state = _scaffoldKey.currentState;
    if (state == null) return;
    state.showBottomSheet(
      (ctx) => _sheetBody(
        ctx,
        title: 'Persistent Bottom Sheet',
        description:
            'Attached to the Scaffold (not a modal route). It stays docked to '
            'the bottom while you interact with the page.',
      ),
    );
  }

  Future<void> _onCupertinoActionSheet() async {
    await _waitDelay();
    if (!mounted) return;
    await showCupertinoModalPopup<void>(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: const Text('Actions'),
        actions: [
          CupertinoActionSheetAction(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Option A'),
          ),
          CupertinoActionSheetAction(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Option B'),
          ),
          CupertinoActionSheetAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Delete'),
          ),
        ],
        cancelButton: CupertinoActionSheetAction(
          onPressed: () => Navigator.of(ctx).pop(),
          child: const Text('Cancel'),
        ),
      ),
    );
  }

  // --- Overlays ---

  Future<void> _onCustomTransitionDialog() async {
    await _waitDelay();
    if (!mounted) return;
    final seconds = _delay == Duration.zero
        ? 8
        : (_delay.inSeconds).clamp(1, 999);
    await showGeneralDialog<void>(
      context: context,
      barrierDismissible: true,
      barrierLabel: 'Dismiss',
      barrierColor: Colors.black54,
      transitionDuration: const Duration(milliseconds: 250),
      pageBuilder: (ctx, animation, secondaryAnimation) => Center(
        child: _CountdownCard(
          title: 'Testing...',
          subtitle: 'Sample subtitle',
          seconds: seconds,
          onClose: () => Navigator.of(ctx).pop(),
        ),
      ),
      transitionBuilder: (context, animation, secondaryAnimation, child) {
        final curved = CurvedAnimation(
          parent: animation,
          curve: Curves.easeOutBack,
        );
        return FadeTransition(
          opacity: animation,
          child: ScaleTransition(scale: curved, child: child),
        );
      },
    );
  }

  Future<void> _onRawOverlay() async {
    await _waitDelay();
    if (!mounted) return;
    final overlay = Overlay.of(context);
    late final OverlayEntry entry;
    void remove() {
      if (entry.mounted) entry.remove();
    }

    entry = OverlayEntry(
      builder: (ctx) => Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              onTap: remove,
              child: const ColoredBox(color: Color(0x80000000)),
            ),
          ),
          Center(
            child: _CountdownCard(
              title: 'Raw OverlayEntry',
              subtitle: 'Inserted directly into the Overlay',
              seconds: 8,
              onClose: remove,
            ),
          ),
        ],
      ),
    );
    overlay.insert(entry);
    Future<void>.delayed(
      _autoDismiss(fallback: const Duration(seconds: 8)),
      remove,
    );
  }

  // --- Notifications ---

  void _onSnackBar() {
    _showSnackBar('This is a SnackBar.');
  }

  void _onMaterialBanner() {
    final messenger = ScaffoldMessenger.of(context);
    messenger.clearMaterialBanners();
    messenger.showMaterialBanner(
      MaterialBanner(
        content: const Text('This is a MaterialBanner.'),
        leading: const Icon(Icons.info_outline),
        actions: [
          TextButton(
            onPressed: messenger.clearMaterialBanners,
            child: const Text('Dismiss'),
          ),
        ],
      ),
    );
    Future<void>.delayed(_autoDismiss(), messenger.clearMaterialBanners);
  }

  // --- Menus & Tooltip ---

  Future<void> _onShowTooltip() async {
    await _waitDelay();
    _tooltipKey.currentState?.ensureTooltipVisible();
  }

  // --- Pickers ---

  Future<void> _onDatePicker() async {
    await _waitDelay();
    if (!mounted) return;
    final now = DateTime.now();
    await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: DateTime(now.year - 5),
      lastDate: DateTime(now.year + 5),
    );
  }

  Future<void> _onTimePicker() async {
    await _waitDelay();
    if (!mounted) return;
    await showTimePicker(context: context, initialTime: TimeOfDay.now());
  }

  // --- Shared helpers ---

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Widget _sheetBody(
    BuildContext ctx, {
    required String title,
    required String description,
  }) {
    return Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: Theme.of(ctx).textTheme.titleLarge),
          const SizedBox(height: 8),
          Text(description),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).maybePop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: _scaffoldKey,
      appBar: AppBar(title: const Text('Dialogs')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Mirrors MAUI's "Show Delay (s)" entry.
            Row(
              children: [
                const Text('Show delay (s):'),
                const SizedBox(width: 12),
                SizedBox(
                  width: 80,
                  child: TextField(
                    controller: _delayController,
                    keyboardType: const TextInputType.numberWithOptions(
                      decimal: true,
                    ),
                    textAlign: TextAlign.center,
                    decoration: const InputDecoration(
                      isDense: true,
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
              ],
            ),

            const _SectionHeader('Alerts'),
            _ButtonGrid(
              children: [
                _DialogButton('Simple Alert', _onSimpleAlert),
                _DialogButton('Accept/Cancel', _onAcceptCancelAlert),
                _DialogButton('Prompt', _onPrompt),
                _DialogButton('Simple Options', _onSimpleOptions),
                _DialogButton('Cupertino Alert', _onCupertinoAlert),
              ],
            ),

            const _SectionHeader('Bottom Sheets'),
            _ButtonGrid(
              children: [
                _DialogButton('Modal Sheet', _onModalSheet),
                _DialogButton('Expandable Sheet', _onExpandableSheet),
                _DialogButton('Persistent Sheet', _onPersistentSheet),
                _DialogButton(
                  'Cupertino Action Sheet',
                  _onCupertinoActionSheet,
                ),
              ],
            ),

            const _SectionHeader('Menus & Tooltip'),
            _ButtonGrid(
              children: [
                Tooltip(
                  key: _tooltipKey,
                  message: 'This is a Flutter tooltip!',
                  child: ElevatedButton(
                    onPressed: _onShowTooltip,
                    child: const Text('Show Tooltip'),
                  ),
                ),
                MenuAnchor(
                  menuChildren: [
                    MenuItemButton(
                      onPressed: () => _showSnackBar('Menu: Option 1'),
                      child: const Text('Option 1'),
                    ),
                    MenuItemButton(
                      onPressed: () => _showSnackBar('Menu: Option 2'),
                      child: const Text('Option 2'),
                    ),
                  ],
                  builder: (ctx, controller, _) => ElevatedButton(
                    onPressed: () => controller.isOpen
                        ? controller.close()
                        : controller.open(),
                    child: const Text('Popup Menu'),
                  ),
                ),
              ],
            ),

            const _SectionHeader('Overlays'),
            _ButtonGrid(
              children: [
                _DialogButton('Custom Transition', _onCustomTransitionDialog),
                _DialogButton('Raw Overlay', _onRawOverlay),
              ],
            ),

            const _SectionHeader('Notifications'),
            _ButtonGrid(
              children: [
                _DialogButton('SnackBar', _onSnackBar),
                _DialogButton('Material Banner', _onMaterialBanner),
              ],
            ),

            const _SectionHeader('Pickers'),
            _ButtonGrid(
              children: [
                _DialogButton('Date Picker', _onDatePicker),
                _DialogButton('Time Picker', _onTimePicker),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// A centered card that counts down from [seconds] and reports completion.
/// Mirrors MAUI's "Centered Popup Card"; the ticking text gives a recording a
/// moving target so masked regions are visibly tracked.
class _CountdownCard extends StatefulWidget {
  const _CountdownCard({
    required this.title,
    required this.subtitle,
    required this.seconds,
    required this.onClose,
  });

  final String title;
  final String subtitle;
  final int seconds;
  final VoidCallback onClose;

  @override
  State<_CountdownCard> createState() => _CountdownCardState();
}

class _CountdownCardState extends State<_CountdownCard> {
  late int _remaining = widget.seconds;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() => _remaining--);
      if (_remaining <= 0) {
        _timer?.cancel();
        widget.onClose();
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final mm = (_remaining ~/ 60).clamp(0, 99).toString().padLeft(2, '0');
    final ss = (_remaining % 60).clamp(0, 59).toString().padLeft(2, '0');
    return Material(
      color: Colors.transparent,
      child: Container(
        width: 300,
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      Text(
                        widget.subtitle,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: widget.onClose,
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text('$mm:$ss', style: Theme.of(context).textTheme.displaySmall),
            Text(
              'Time Remaining',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: widget.onClose,
              child: const Text('Stop'),
            ),
          ],
        ),
      ),
    );
  }
}

class _DialogButton extends StatelessWidget {
  const _DialogButton(this.label, this.onPressed);

  final String label;
  final FutureOr<void> Function() onPressed;

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(onPressed: () => onPressed(), child: Text(label));
  }
}

class _ButtonGrid extends StatelessWidget {
  const _ButtonGrid({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Wrap(spacing: 8, runSpacing: 8, children: children);
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 16, bottom: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
          ),
          const Divider(height: 12, thickness: 1),
        ],
      ),
    );
  }
}
