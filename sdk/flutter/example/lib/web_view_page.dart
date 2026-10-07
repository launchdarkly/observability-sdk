import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Shows a dialog containing an embedded [WebViewWidget], used to exercise the
/// `maskWebViews` Session Replay privacy option.
///
/// A web view is hosted by a Flutter *platform view*; when `maskWebViews` is
/// enabled the capture pipeline (see the SDK's `MaskingPolicy`) redacts the
/// platform view's region so the page contents don't appear in a recorded
/// session. Wrap the [WebViewWidget] in `LDUnmask` to reveal it again, or in
/// `LDMask` to always redact it regardless of the global setting.
Future<void> showWebViewDialog(BuildContext context) {
  return showDialog<void>(
    context: context,
    builder: (_) => const WebViewDialog(),
  );
}

class WebViewDialog extends StatefulWidget {
  const WebViewDialog({super.key});

  @override
  State<WebViewDialog> createState() => _WebViewDialogState();
}

class _WebViewDialogState extends State<WebViewDialog> {
  late final WebViewController _controller;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..loadRequest(Uri.parse('https://launchdarkly.com'));
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      insetPadding: const EdgeInsets.all(16),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480, maxHeight: 600),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
              child: Row(
                children: [
                  const Expanded(
                    child: Text(
                      'Web View',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    tooltip: 'Close',
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            // The web view is a platform view; with `maskWebViews: true` its
            // region is redacted in Session Replay recordings.
            Expanded(child: WebViewWidget(controller: _controller)),
          ],
        ),
      ),
    );
  }
}
