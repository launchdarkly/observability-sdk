import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// Flutter port of `sdk/@launchdarkly/mobile-dotnet/sample/CreditCardPage.xaml`
/// + `CreditCardPage.xaml.cs`. Demonstrates form behavior intended for the
/// Session Replay masking story: a card number, expiry, and CVC that should
/// never appear in a recorded session, plus a brand label that the MAUI
/// sample explicitly opts in to masking via `BrandLabel.LDMask()`.
class CreditCardPage extends StatefulWidget {
  const CreditCardPage({super.key});

  @override
  State<CreditCardPage> createState() => _CreditCardPageState();
}

enum _CardBrand { unknown, visa, mastercard, amex, discover }

class _CreditCardPageState extends State<CreditCardPage> {
  final _nameController = TextEditingController();
  final _numberController = TextEditingController();
  final _expiryController = TextEditingController();
  final _cvcController = TextEditingController();

  String _brandText = 'Card';
  String _maskedNumberText = '';

  String? _nameError;
  String? _numberError;
  String? _expiryError;
  String? _cvcError;

  // Re-entry guard: each formatting listener writes back into its controller
  // (e.g. "12345" -> "1234 5"), which retriggers the listener. Without a guard
  // we'd recurse once before the equality check stabilizes; this just skips
  // the re-entrant pass cleanly.
  bool _suppressReformat = false;

  @override
  void initState() {
    super.initState();
    _nameController.addListener(_onNameChanged);
    _numberController.addListener(_onNumberChanged);
    _expiryController.addListener(_onExpiryChanged);
    _cvcController.addListener(_onCvcChanged);

    // Masking demo. The screen-wide `PrivacyOptions(maskTextInputs: true)` from
    // `main.dart` masks every text field on this page by default. On top of
    // that:
    //  - the brand label is wrapped in `LDMask` (MAUI's `BrandLabel.LDMask()`)
    //    to redact it even though global label masking is off; and
    //  - the cardholder-name field is wrapped in `LDUnmask` to reveal just that
    //    one field, overriding the global `maskTextInputs` policy.
  }

  @override
  void dispose() {
    _nameController.dispose();
    _numberController.dispose();
    _expiryController.dispose();
    _cvcController.dispose();
    super.dispose();
  }

  // --- Field listeners (mirror C# OnXxxTextChanged handlers) ---

  void _onNameChanged() {
    if (_nameError != null) {
      setState(() => _nameError = null);
    }
  }

  void _onNumberChanged() {
    if (_suppressReformat) return;

    var digits = _extractDigits(_numberController.text);
    final brand = _detectBrand(digits);
    digits = _limitDigitsForBrand(digits, brand);
    final formatted = _formatNumberForBrand(digits, brand);

    if (_numberController.text != formatted) {
      _setControllerText(_numberController, formatted);
    }

    setState(() {
      _brandText = _brandToDisplayName(brand);
      _maskedNumberText = _getMaskedLast4(digits);
      _numberError = null;
    });

    _adjustCvcForBrand(brand);
  }

  void _onExpiryChanged() {
    if (_suppressReformat) return;

    var digits = _extractDigits(_expiryController.text);
    if (digits.length > 4) digits = digits.substring(0, 4);

    final formatted = digits.length <= 2
        ? digits
        : '${digits.substring(0, 2)}/${digits.substring(2)}';

    if (_expiryController.text != formatted) {
      _setControllerText(_expiryController, formatted);
    }

    if (_expiryError != null) {
      setState(() => _expiryError = null);
    }
  }

  void _onCvcChanged() {
    if (_suppressReformat) return;

    var digits = _extractDigits(_cvcController.text);
    final brand = _detectBrand(_extractDigits(_numberController.text));
    final maxLen = brand == _CardBrand.amex ? 4 : 3;
    if (digits.length > maxLen) digits = digits.substring(0, maxLen);

    if (_cvcController.text != digits) {
      _setControllerText(_cvcController, digits);
    }

    if (_cvcError != null) {
      setState(() => _cvcError = null);
    }
  }

  void _setControllerText(TextEditingController c, String text) {
    _suppressReformat = true;
    try {
      c.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
    } finally {
      _suppressReformat = false;
    }
  }

  // --- Save / validate ---

  Future<void> _onSavePressed() async {
    final result = _validateAll();
    if (!result.isValid) return;

    final masked = _getMaskedLast4(result.digits);
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Saved'),
        content: Text(
          '${_brandToDisplayName(result.brand)} $masked saved.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  _ValidationResult _validateAll() {
    var isValid = true;
    String? nameErr;
    String? numberErr;
    String? expiryErr;
    String? cvcErr;

    final name = _nameController.text.trim();
    if (name.length < 2) {
      nameErr = 'Enter the cardholder name.';
      isValid = false;
    }

    final digits = _extractDigits(_numberController.text);
    final brand = _detectBrand(digits);
    final expectedLen = _expectedPanLengthForBrand(brand);
    if (digits.length != expectedLen || !_isValidLuhn(digits)) {
      numberErr = 'Enter a valid card number.';
      isValid = false;
    }

    final expiryDate = _tryParseExpiry(_expiryController.text);
    if (expiryDate == null || _isExpired(expiryDate)) {
      expiryErr = 'Enter a valid future date.';
      isValid = false;
    }

    final cvcDigits = _extractDigits(_cvcController.text);
    final cvcLen = brand == _CardBrand.amex ? 4 : 3;
    if (cvcDigits.length != cvcLen) {
      cvcErr = 'CVC must be $cvcLen digits.';
      isValid = false;
    }

    setState(() {
      _nameError = nameErr;
      _numberError = numberErr;
      _expiryError = expiryErr;
      _cvcError = cvcErr;
    });

    return _ValidationResult(
      isValid: isValid,
      brand: brand,
      digits: digits,
    );
  }

  void _adjustCvcForBrand(_CardBrand brand) {
    var digits = _extractDigits(_cvcController.text);
    final maxLen = brand == _CardBrand.amex ? 4 : 3;
    if (digits.length > maxLen) digits = digits.substring(0, maxLen);
    if (_cvcController.text != digits) {
      _setControllerText(_cvcController, digits);
    }
  }

  // --- Pure helpers ---

  static String _extractDigits(String value) {
    if (value.isEmpty) return '';
    final buf = StringBuffer();
    for (final code in value.codeUnits) {
      if (code >= 0x30 && code <= 0x39) buf.writeCharCode(code);
    }
    return buf.toString();
  }

  static _CardBrand _detectBrand(String digits) {
    if (digits.isEmpty) return _CardBrand.unknown;

    if (digits.startsWith('4')) return _CardBrand.visa;

    if (digits.startsWith('34') || digits.startsWith('37')) {
      return _CardBrand.amex;
    }

    if (digits.length >= 2) {
      final two = int.tryParse(digits.substring(0, 2));
      if (two != null && two >= 51 && two <= 55) {
        return _CardBrand.mastercard;
      }
    }
    if (digits.length >= 4) {
      final four = int.tryParse(digits.substring(0, 4));
      if (four != null && four >= 2221 && four <= 2720) {
        return _CardBrand.mastercard;
      }
    }

    if (digits.startsWith('6011') || digits.startsWith('65')) {
      return _CardBrand.discover;
    }
    if (digits.length >= 3) {
      final three = int.tryParse(digits.substring(0, 3));
      if (three != null && three >= 644 && three <= 649) {
        return _CardBrand.discover;
      }
    }

    return _CardBrand.unknown;
  }

  static int _expectedPanLengthForBrand(_CardBrand brand) {
    return brand == _CardBrand.amex ? 15 : 16;
  }

  static String _limitDigitsForBrand(String digits, _CardBrand brand) {
    final max = _expectedPanLengthForBrand(brand);
    return digits.length > max ? digits.substring(0, max) : digits;
  }

  static String _formatNumberForBrand(String digits, _CardBrand brand) {
    if (digits.isEmpty) return '';

    if (brand == _CardBrand.amex) {
      // 4-6-5
      if (digits.length <= 4) return digits;
      final parts = <String>[digits.substring(0, 4)];
      if (digits.length <= 10) {
        parts.add(digits.substring(4));
      } else {
        parts.add(digits.substring(4, 10));
        parts.add(digits.substring(10));
      }
      return parts.join(' ');
    }

    final parts = <String>[];
    for (var i = 0; i < digits.length; i += 4) {
      final end = (i + 4) < digits.length ? i + 4 : digits.length;
      parts.add(digits.substring(i, end));
    }
    return parts.join(' ');
  }

  static String _brandToDisplayName(_CardBrand brand) {
    switch (brand) {
      case _CardBrand.visa:
        return 'Visa';
      case _CardBrand.mastercard:
        return 'Mastercard';
      case _CardBrand.amex:
        return 'American Express';
      case _CardBrand.discover:
        return 'Discover';
      case _CardBrand.unknown:
        return 'Card';
    }
  }

  static String _getMaskedLast4(String digits) {
    if (digits.isEmpty) return '';
    final last4 = digits.length <= 4
        ? digits
        : digits.substring(digits.length - 4);
    return '\u2022\u2022\u2022\u2022 $last4';
  }

  static bool _isValidLuhn(String digits) {
    if (digits.length < 12) return false;

    var sum = 0;
    var doubleIt = false;
    for (var i = digits.length - 1; i >= 0; i--) {
      final code = digits.codeUnitAt(i) - 0x30;
      if (code < 0 || code > 9) return false;
      var d = code;
      if (doubleIt) {
        d *= 2;
        if (d > 9) d -= 9;
      }
      sum += d;
      doubleIt = !doubleIt;
    }
    return sum % 10 == 0;
  }

  /// Returns the last millisecond of the parsed MM/YY month (UTC), or null
  /// if `value` doesn't match `MM/YY` with 1 <= MM <= 12.
  static DateTime? _tryParseExpiry(String value) {
    final parts = value.split('/');
    if (parts.length != 2) return null;
    if (parts[0].length != 2 || parts[1].length != 2) return null;

    final mm = int.tryParse(parts[0]);
    final yy = int.tryParse(parts[1]);
    if (mm == null || yy == null) return null;
    if (mm < 1 || mm > 12) return null;

    final year = 2000 + yy;
    // DateTime.utc normalizes month overflow, so passing mm + 1 is safe even
    // when mm == 12 (rolls into the next year automatically).
    final firstOfNext = DateTime.utc(year, mm + 1, 1);
    return firstOfNext.subtract(const Duration(milliseconds: 1));
  }

  static bool _isExpired(DateTime lastMomentUtc) {
    return DateTime.now().toUtc().isAfter(lastMomentUtc);
  }

  // --- Build ---

  @override
  Widget build(BuildContext context) {
    final hintColor = Theme.of(context).hintColor;

    return Scaffold(
      appBar: AppBar(title: const Text('Credit Card')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Enter your card details',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 12),

            Row(
              children: [
                // Mirrors the MAUI sample's `BrandLabel.LDMask()`: explicitly
                // redact this label (global label masking is off, so without
                // LDMask it would be visible in the recording).
                Expanded(child: LDMask(child: Text(_brandText))),
                Text(
                  _maskedNumberText,
                  style: TextStyle(color: hintColor),
                ),
              ],
            ),
            const SizedBox(height: 12),

            const Text('Cardholder name'),
            const SizedBox(height: 4),
            // LDUnmask reveals this field in session replay, overriding the
            // screen-wide maskTextInputs policy. (Demo only — choose carefully
            // which fields are safe to reveal.)
            LDUnmask(
              child: TextField(
                controller: _nameController,
                decoration: const InputDecoration(
                  hintText: 'Full name as on card',
                  border: OutlineInputBorder(),
                ),
                textInputAction: TextInputAction.next,
                autofillHints: const [AutofillHints.creditCardName],
              ),
            ),
            if (_nameError != null) _ErrorText(_nameError!),
            const SizedBox(height: 12),

            const Text('Card number'),
            const SizedBox(height: 4),
            TextField(
              controller: _numberController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                hintText: '1234 5678 9012 3456',
                border: OutlineInputBorder(),
              ),
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.creditCardNumber],
            ),
            if (_numberError != null) _ErrorText(_numberError!),
            const SizedBox(height: 12),

            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Text('Expiry (MM/YY)'),
                      const SizedBox(height: 4),
                      TextField(
                        controller: _expiryController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          hintText: 'MM/YY',
                          border: OutlineInputBorder(),
                        ),
                        textInputAction: TextInputAction.next,
                      ),
                      if (_expiryError != null) _ErrorText(_expiryError!),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Text('CVC'),
                      const SizedBox(height: 4),
                      TextField(
                        controller: _cvcController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          hintText: '123',
                          border: OutlineInputBorder(),
                        ),
                        textInputAction: TextInputAction.done,
                      ),
                      if (_cvcError != null) _ErrorText(_cvcError!),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            ElevatedButton(
              onPressed: _onSavePressed,
              child: const Text('Save card'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorText extends StatelessWidget {
  const _ErrorText(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Text(
        text,
        style: const TextStyle(color: Color(0xFFB00020), fontSize: 12),
      ),
    );
  }
}

class _ValidationResult {
  const _ValidationResult({
    required this.isValid,
    required this.brand,
    required this.digits,
  });

  final bool isValid;
  final _CardBrand brand;
  final String digits;
}
