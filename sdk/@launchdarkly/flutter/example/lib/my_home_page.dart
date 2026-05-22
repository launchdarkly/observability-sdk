import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

import 'credit_card_page.dart';
import 'main.dart';

// NOTE: The MAUI sample also has a "Metric" subsection (gauge, histogram,
// count, incremental, up-down counter) under Observability. The Flutter
// observability SDK does not currently expose a metric API on `Observe`,
// so that subsection is intentionally omitted. Add it here once the SDK
// surface for metrics lands.

/// Returns a friendly name for the current platform, e.g. "Flutter Android".
String _platformName() {
  if (kIsWeb) return 'Flutter Web';
  switch (defaultTargetPlatform) {
    case TargetPlatform.android:
      return 'Flutter Android';
    case TargetPlatform.iOS:
      return 'Flutter iOS';
    case TargetPlatform.macOS:
      return 'Flutter macOS';
    case TargetPlatform.windows:
      return 'Flutter Windows';
    case TargetPlatform.linux:
      return 'Flutter Linux';
    case TargetPlatform.fuchsia:
      return 'Flutter Fuchsia';
  }
}

/// Returns a Material icon that visually represents the current platform.
IconData _platformIcon() {
  if (kIsWeb) return Icons.web;
  switch (defaultTargetPlatform) {
    case TargetPlatform.android:
      return Icons.android;
    case TargetPlatform.iOS:
      return Icons.phone_iphone;
    case TargetPlatform.macOS:
      return Icons.laptop_mac;
    case TargetPlatform.windows:
      return Icons.laptop_windows;
    case TargetPlatform.linux:
      return Icons.computer;
    case TargetPlatform.fuchsia:
      return Icons.devices_other;
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final http.Client _httpClient = http.Client();
  final TextEditingController _customLogController = TextEditingController();
  final TextEditingController _customSpanController = TextEditingController();
  final TextEditingController _flagKeyController = TextEditingController(
    text: 'my-feature',
  );

  Timer? _pollingTimer;
  int _counter = 0;

  @override
  void dispose() {
    _httpClient.close();
    _customLogController.dispose();
    _customSpanController.dispose();
    _flagKeyController.dispose();
    _pollingTimer?.cancel();
    super.dispose();
  }

  // --- Identify ---

  void _onIdentifyUser() {
    final userContext = LDContextBuilder()
        .kind('user', 'single-userkey')
        .name('Bob Smith')
        .build();
    LDSingleton.client?.identify(userContext);
    debugPrint('Identified as User');
  }

  void _onIdentifyMulti() {
    final multiContext = LDContextBuilder()
        .kind('user', 'multi-username')
        .name('multi-username')
        .kind('device', 'iphone')
        .name('iphone')
        .build();
    LDSingleton.client?.identify(multiContext);
    debugPrint('Identified as Multi');
  }

  void _onIdentifyAnon() {
    final anonContext = LDContextBuilder()
        .kind('user', 'anonymous-userkey')
        .anonymous(true)
        .build();
    LDSingleton.client?.identify(anonContext);
    debugPrint('Identified as Anonymous');
  }

  // --- Instrumentation ---

  Future<void> _onTriggerHttpRequest() async {
    try {
      final response = await _httpClient.get(Uri.parse('https://www.google.com'));
      debugPrint('HTTP Response: ${response.statusCode}');
    } catch (e) {
      debugPrint('HTTP Request failed: $e');
    }
  }

  void _onTriggerCrash() {
    throw StateError('Failed to connect to bogus server.');
  }

  // --- Customer API ---

  void _onTriggerError() {
    final inner = StateError('The error that caused the other error.');
    final exception = Exception('Manual error womp womp: $inner');
    Observe.recordException(exception, stackTrace: StackTrace.current);
    debugPrint('Error triggered');
  }

  void _onTriggerLog() {
    Observe.recordLog(
      'Test Log',
      severity: 'info',
      attributes: <String, Attribute>{
        'test-string': StringAttribute('flutter'),
        'test-true': BooleanAttribute(true),
        'test-false': BooleanAttribute(false),
        'test-integer': IntAttribute(42),
        'test-double': DoubleAttribute(3.14),
        'test-array': DoubleListAttribute([3.14, 6.28]),
      },
    );
    debugPrint('Log triggered');
  }

  // The MAUI sample captures the span context inside a span and passes it to
  // a log on a detached task. The Flutter Observe API does not expose a span
  // context type, but a log recorded while a span is active is automatically
  // associated with that span via the OTel context.
  Future<void> _onTriggerLogWithContext() async {
    final span = Observe.startSpan('log-context-demo');
    span.setAttribute('demo', StringAttribute('log-with-context'));
    Observe.recordLog(
      'Log with span context',
      severity: 'warn',
      attributes: <String, Attribute>{
        'source': StringAttribute('detached-task-demo'),
      },
    );
    span.end();
    debugPrint('Log with Context triggered');
  }

  void _onTriggerErrorLogWithStack() {
    Observe.recordLog(
      'This is an error log!',
      severity: 'error',
      stackTrace: StackTrace.current,
      attributes: <String, Attribute>{
        'attribute-in-log': StringAttribute('value-in-log'),
      },
    );
    debugPrint('Error log with stack trace triggered');
  }

  void _onSendCustomLog() {
    final message = _customLogController.text;
    if (message.isEmpty) return;
    Observe.recordLog(message, severity: 'info');
    debugPrint('Custom log sent: $message');
  }

  Future<void> _onTriggerNestedSpans() async {
    final span0 = Observe.startSpan('NestedSpan');
    final span1 = Observe.startSpan('NestedSpan1');
    final span2 = Observe.startSpan('NestedSpan2');

    Observe.recordLog('NestedLog', severity: 'info');

    try {
      await _httpClient.get(Uri.parse('https://www.google.com'));
    } catch (e) {
      debugPrint('HTTP Request failed: $e');
    }

    span2.end();
    span1.end();
    span0.end();
    debugPrint('Nested Spans triggered');
  }

  void _onTriggerSequentialSpans() {
    final span1 = Observe.startSpan('SequentialSpan1');
    span1.setAttribute('sequence', StringAttribute('1'));
    span1.end();

    final span2 = Observe.startSpan('SequentialSpan2');
    span2.setAttribute('sequence', StringAttribute('2'));
    span2.end();

    final span3 = Observe.startSpan('SequentialSpan3');
    span3.setAttribute('sequence', StringAttribute('3'));
    span3.end();

    debugPrint('Sequential independent spans triggered');
  }

  void _onSendCustomSpan() {
    final spanName = _customSpanController.text;
    if (spanName.isEmpty) return;
    final span = Observe.startSpan(spanName);
    span.setAttribute('custom_span', BooleanAttribute(true));
    span.addEvent('cache.miss');
    span.addEvent('retry.started');
    span.addEvent('download.completed');
    span.end();
    debugPrint('Custom span sent: $spanName');
  }

  void _onStartPolling() {
    final root = Observe.startSpan('StartPolling');
    setState(() {
      _pollingTimer = Timer.periodic(const Duration(seconds: 30), (_) {
        final pollSpan = Observe.startSpan('PollTick');
        pollSpan.setAttribute(
          'tick.time',
          StringAttribute(DateTime.now().toUtc().toIso8601String()),
        );
        pollSpan.end();
      });
    });
    root.end();
  }

  void _onStopPolling() {
    setState(() {
      _pollingTimer?.cancel();
      _pollingTimer = null;
    });
  }

  Future<void> _onEvaluateFlag() async {
    final flagKey = _flagKeyController.text;
    if (flagKey.isEmpty) {
      await _showAlert('Flag', 'Flag key cannot be empty');
      return;
    }
    final result =
        LDSingleton.client?.boolVariation(flagKey, false) ?? false;
    if (!mounted) return;
    await _showAlert('Flag', '$flagKey: $result');
    debugPrint('Flag $flagKey: $result');
  }

  Future<void> _showAlert(String title, String message) {
    return showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  // --- Counter (kept from previous example) ---

  void _incrementCounter() {
    final span = Observe.startSpan('increment-counter-1');
    final span2 = Observe.startSpan('increment-counter-2');

    LDSingleton.client?.stringVariation('textFlag', 'default');
    setState(() {
      _counter++;
    });
    span2.end();
    span.end();
  }

  @override
  Widget build(BuildContext context) {
    final dangerStyle = ElevatedButton.styleFrom(
      backgroundColor: const Color(0xFFF2B8B5),
      foregroundColor: Colors.white,
    );

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(_platformIcon()),
            const SizedBox(width: 8),
            Text(_platformName()),
          ],
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            const _SectionHeader('Session Replay'),
            // Mirrors the "Masking" subsection of the MAUI sample's MainPage,
            // which exposes Credit Card / Number Pad / Dialogs entries that
            // each demonstrate session replay masking. Only the Credit Card
            // page is ported so far.
            ElevatedButton(
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const CreditCardPage(),
                  ),
                );
              },
              child: const Text('Credit Card'),
            ),

            const _SectionHeader('Observability'),

            const _SubsectionHeader('Identify'),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ElevatedButton(
                  onPressed: _onIdentifyUser,
                  child: const Text('User'),
                ),
                ElevatedButton(
                  onPressed: _onIdentifyMulti,
                  child: const Text('Multi'),
                ),
                ElevatedButton(
                  onPressed: _onIdentifyAnon,
                  child: const Text('Anon'),
                ),
              ],
            ),

            const _SubsectionHeader('Instrumentation'),
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: ElevatedButton(
                onPressed: _onTriggerHttpRequest,
                child: const Text('Trigger HTTP Request'),
              ),
            ),
            ElevatedButton(
              onPressed: _onTriggerCrash,
              style: dangerStyle,
              child: const Text('Trigger Crash'),
            ),

            const _SubsectionHeader('Customer API'),
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: ElevatedButton(
                onPressed: _onTriggerError,
                style: dangerStyle,
                child: const Text('Trigger Error'),
              ),
            ),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ElevatedButton(
                  onPressed: _onTriggerLog,
                  child: const Text('Trigger Log'),
                ),
                ElevatedButton(
                  onPressed: _onTriggerLogWithContext,
                  child: const Text('Log with Context'),
                ),
                ElevatedButton(
                  onPressed: _onTriggerErrorLogWithStack,
                  child: const Text('Record error log with stack trace'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _customLogController,
              decoration: const InputDecoration(
                hintText: 'Log Message',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _onSendCustomLog,
              child: const Text('Send custom log'),
            ),

            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ElevatedButton(
                  onPressed: _onTriggerNestedSpans,
                  child: const Text('Nested Spans'),
                ),
                ElevatedButton(
                  onPressed: _pollingTimer == null
                      ? _onStartPolling
                      : _onStopPolling,
                  child: Text(
                    _pollingTimer == null ? 'Start Polling' : 'Stop Polling',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            ElevatedButton(
              onPressed: _onTriggerSequentialSpans,
              child: const Text('Trigger Sequential Spans'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _customSpanController,
              decoration: const InputDecoration(
                hintText: 'Span Name',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _onSendCustomSpan,
              child: const Text('Send custom span'),
            ),

            const SizedBox(height: 16),
            TextField(
              controller: _flagKeyController,
              decoration: const InputDecoration(
                hintText: 'Flag key',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _onEvaluateFlag,
              child: const Text('Evaluate boolean flag'),
            ),

            const _SubsectionHeader('Other'),
            ElevatedButton(
              onPressed: () {
                debugPrint('This is a message from debug print');
              },
              child: const Text('Call debugPrint'),
            ),
            const SizedBox(height: 4),
            ElevatedButton(
              onPressed: () {
                // ignore: avoid_print
                print('This is a message from print');
              },
              child: const Text('Call print'),
            ),

            const SizedBox(height: 16),
            const Text('You have pushed the button this many times:'),
            Text(
              '$_counter',
              style: Theme.of(context).textTheme.headlineMedium,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _incrementCounter,
        tooltip: 'Increment',
        child: const Icon(Icons.add),
      ),
    );
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

class _SubsectionHeader extends StatelessWidget {
  const _SubsectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 12, bottom: 4),
      child: Text(
        title,
        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
      ),
    );
  }
}
