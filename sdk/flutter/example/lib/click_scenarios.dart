import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// Buttons and controls covering every way a click can be described, for
/// checking both pipelines by hand: each tap should produce one `click` span
/// (`event.tag`, `event.id`, `event.text`, `event.xpath`, `event.x`/`event.y`)
/// and one Session Replay `Click` marker on the replay timeline.
///
/// What to look for, per row:
///
/// - **Plain button** — described by type and label alone, no tagging needed.
/// - **Keyed button** — its `ValueKey` becomes `event.id`.
/// - **Tagged button** — an [LDClick] id wins over the key, and its properties
///   ride along on the span.
/// - **Design-system button** — named by the `customClickTargetResolver`
///   registered in `main.dart`, not by the `InkWell` it is built from.
/// - **Icon button** — has no text, so its tooltip describes it.
/// - **Disabled button** — must produce *nothing*: it is painted and it contains
///   the tap point, but pressing it does nothing.
/// - **List tile with a trailing action** — tapping the row reports `ListTile`,
///   tapping the icon reports `IconButton`.
/// - **Bare gesture detector** — recognized even though it is not a button.
/// - **Switch** — reported without its state being mistaken for a label.
/// - **Masked button** — inside [LDMask], so the click is reported but its label
///   is not, the same redaction that covers it in the recording.
/// - **Text field** — focusing it is not a click, and nothing typed into it can
///   ever reach `event.text`.
/// - **Manual click** — [LDObserve.trackClick] for an interaction automatic
///   capture cannot see.
class ClickScenarios extends StatefulWidget {
  const ClickScenarios({super.key});

  @override
  State<ClickScenarios> createState() => _ClickScenariosState();
}

class _ClickScenariosState extends State<ClickScenarios> {
  final TextEditingController _noteController = TextEditingController();
  bool _notifications = true;
  bool _muted = false;

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  void _noop() {}

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            ElevatedButton(onPressed: _noop, child: const Text('Plain button')),
            ElevatedButton(
              key: const ValueKey('clicks.keyed'),
              onPressed: _noop,
              child: const Text('Keyed button'),
            ),
            LDClick(
              id: 'clicks.tagged',
              properties: const {'demo_flow': 'clicks'},
              child: ElevatedButton(
                key: const ValueKey('clicks.key-loses-to-marker'),
                onPressed: _noop,
                child: const Text('Tagged button'),
              ),
            ),
            DemoPrimaryButton(label: 'Design-system button', onPressed: _noop),
            IconButton(
              onPressed: _noop,
              tooltip: 'Refresh feed',
              icon: const Icon(Icons.refresh),
            ),
            const ElevatedButton(
              onPressed: null,
              child: Text('Disabled button'),
            ),
            LDMask(
              child: ElevatedButton(
                onPressed: _noop,
                child: const Text('Dr. Smith, 2pm'),
              ),
            ),
            ElevatedButton(
              onPressed: () => LDObserve.trackClick(
                id: 'clicks.manual',
                tag: 'ShakeGesture',
                text: 'Manual click',
                properties: const {'source': 'example'},
              ),
              child: const Text('Report a manual click'),
            ),
          ],
        ),
        const SizedBox(height: 8),
        ListTile(
          onTap: _noop,
          title: const Text('List tile row'),
          subtitle: const Text('Row reports ListTile, icon reports IconButton'),
          trailing: IconButton(
            onPressed: () => setState(() => _muted = !_muted),
            tooltip: _muted ? 'Unmute' : 'Mute',
            icon: Icon(_muted ? Icons.volume_off : Icons.volume_up),
          ),
        ),
        GestureDetector(
          onTap: _noop,
          child: Container(
            padding: const EdgeInsets.all(12),
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: const Text('Bare GestureDetector'),
          ),
        ),
        SwitchListTile(
          value: _notifications,
          onChanged: (value) => setState(() => _notifications = value),
          title: const Text('Notifications'),
        ),
        TextField(
          controller: _noteController,
          decoration: const InputDecoration(
            hintText: 'Type here — never reported as click text',
            border: OutlineInputBorder(),
          ),
        ),
      ],
    );
  }
}

/// Stands in for a design system's own button: a type the SDK knows nothing
/// about until an app registers it through `customClickTargetResolver`.
///
/// Without that registration a tap here reports the `InkWell` inside
/// [ElevatedButton] — technically true and useless for grouping.
class DemoPrimaryButton extends StatelessWidget {
  final String label;
  final VoidCallback onPressed;

  const DemoPrimaryButton({
    super.key,
    required this.label,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) =>
      ElevatedButton(onPressed: onPressed, child: Text(label));
}

/// Names this app's own widget types for click tracking, wired into
/// `AnalyticsOptions.customClickTargetResolver` in `main.dart`.
///
/// Returns null for everything else so the built-in Material and Cupertino
/// rules still apply. The tag is a string literal rather than
/// `runtimeType.toString()`, which `--obfuscate` would mangle.
LDClickTargetInfo? demoClickTargetResolver(Widget widget) => switch (widget) {
  DemoPrimaryButton(:final label) => LDClickTargetInfo(
    tag: 'DemoPrimaryButton',
    text: label,
  ),
  _ => null,
};
