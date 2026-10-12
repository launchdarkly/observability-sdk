/// Recovery of the Dart AOT snapshot build id, surfaced everywhere as
/// `symbols_id`, from a stack trace.
///
/// A release Flutter build (`--obfuscate --split-debug-info`) prints an
/// obfuscation header in every stack trace, including a `build_id: '<hex>'`
/// line. That id matches the `.symbols` file `ldcli` uploads, so reporting it as
/// a resource attribute lets the backend resolve the crash against the uploaded
/// symbol map (the Id lane). In debug/profile builds and on web there is no such
/// header, so extraction returns null and symbolication simply doesn't apply.
library;

/// Resource attribute carrying the Dart AOT snapshot build id (surfaced as
/// symbols_id). The backend keys the Id-lane symbol map by this value. Shared by
/// the Dart OpenTelemetry [Resource] (web) and the native init options (mobile),
/// so both export paths report the same key.
const String symbolsIdAttributeKey = 'launchdarkly.symbols_id';

/// Matches the `build_id: '<hex>'` line Dart prints in obfuscated stack traces.
/// Tolerant of the `:`/`=` separator and optional quotes across Dart versions.
final RegExp _buildIdPattern = RegExp(
  r"build_id(?:\s*[:=]\s*|\s+)'?([0-9a-fA-F]+)'?",
);

/// Reads the `symbols_id` (Dart snapshot build id) from [stackTrace], defaulting
/// to [StackTrace.current]. Returns the id as lowercase hex, or null when the
/// trace carries no obfuscation header (debug/profile builds, web).
String? readSymbolsId([StackTrace? stackTrace]) {
  final trace = (stackTrace ?? StackTrace.current).toString();
  final match = _buildIdPattern.firstMatch(trace);
  if (match == null) {
    return null;
  }
  return match.group(1)!.toLowerCase();
}
