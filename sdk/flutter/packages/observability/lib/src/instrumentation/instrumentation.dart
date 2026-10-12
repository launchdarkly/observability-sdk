/// Interfaces which instrumentations should implement.
abstract interface class Instrumentation {
  /// Stops the instrumentation and releases any hooks or resources it holds.
  void dispose();
}
