import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// Flutter port of
/// `swift-launchdarkly-observability/TestApp/Sources/SessionReplay/Masking/NestedMaskingPropagationView.swift`.
///
/// Demonstrates how masking decisions propagate down a widget subtree:
///  1. a baseline field that the screen-wide `maskTextInputs` policy redacts;
///  2. an ancestor wrapped in [LDUnmask], revealing its descendant field;
///  3. an ancestor wrapped in [LDMask], redacting every descendant (even a
///     plain label that would normally be visible); and
///  4. a deeply nested field under [LDUnmask] to show propagation still
///     applies through multiple layers of nesting.
class NestedMaskingPropagationPage extends StatefulWidget {
  const NestedMaskingPropagationPage({super.key});

  @override
  State<NestedMaskingPropagationPage> createState() =>
      _NestedMaskingPropagationPageState();
}

class _NestedMaskingPropagationPageState
    extends State<NestedMaskingPropagationPage> {
  final _baselineController = TextEditingController();
  final _unmaskedController = TextEditingController();
  final _maskedController = TextEditingController();
  final _deepController = TextEditingController();

  @override
  void dispose() {
    _baselineController.dispose();
    _unmaskedController.dispose();
    _maskedController.dispose();
    _deepController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ancestor Mask Propagation')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _Section(
              title: '1. Baseline (no modifier)',
              note: 'Globally masked by maskTextInputs.',
              child: TextField(
                controller: _baselineController,
                decoration: const InputDecoration(
                  hintText: 'type here',
                  border: OutlineInputBorder(),
                ),
              ),
            ),
            const SizedBox(height: 24),

            _Section(
              title: '2. Ancestor LDUnmask',
              note:
                  'Parent container is unmasked — child TextField should be '
                  'visible despite maskTextInputs=true.',
              // LDUnmask on the ancestor reveals the descendant field even
              // though the screen-wide maskTextInputs policy would redact it.
              child: LDUnmask(
                child: Container(
                  padding: const EdgeInsets.all(8),
                  color: Colors.green.withValues(alpha: 0.15),
                  child: TextField(
                    controller: _unmaskedController,
                    decoration: const InputDecoration(
                      hintText: 'visible inside unmasked ancestor',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 24),

            _Section(
              title: '3. Ancestor LDMask',
              note:
                  'Parent container is masked — all children get covered, '
                  'even the plain Text label.',
              // LDMask on the ancestor redacts the whole subtree, including the
              // plain label that would otherwise be visible.
              child: LDMask(
                child: Container(
                  padding: const EdgeInsets.all(8),
                  color: Colors.red.withValues(alpha: 0.15),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('plain label that would normally be visible'),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _maskedController,
                        decoration: const InputDecoration(
                          hintText: 'textfield inside masked ancestor',
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 24),

            _Section(
              title: '4. Deep unmask through nesting',
              note:
                  'Two levels of nesting under LDUnmask — propagation should '
                  'still apply.',
              child: LDUnmask(
                child: Container(
                  padding: const EdgeInsets.all(8),
                  color: Colors.green.withValues(alpha: 0.15),
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    color: Colors.green.withValues(alpha: 0.05),
                    child: TextField(
                      controller: _deepController,
                      decoration: const InputDecoration(
                        hintText: 'deeply nested, still unmasked',
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Titled section with a caption note, mirroring the Swift `section(...)`
/// helper used by `NestedMaskingPropagationView`.
class _Section extends StatelessWidget {
  const _Section({
    required this.title,
    required this.note,
    required this.child,
  });

  final String title;
  final String note;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: theme.textTheme.titleMedium),
        const SizedBox(height: 6),
        Text(
          note,
          style: theme.textTheme.bodySmall?.copyWith(color: theme.hintColor),
        ),
        const SizedBox(height: 8),
        child,
      ],
    );
  }
}
