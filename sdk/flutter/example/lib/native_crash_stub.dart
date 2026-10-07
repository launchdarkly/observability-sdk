// Web implementation: there is no process to terminate and no isolates to
// spawn, so every entry point reports that it is unavailable. These scenarios
// are filtered out of the picker on web, so this should never run.

Never _unsupported(String what) =>
    throw UnsupportedError('$what is only available on native platforms.');

void crashWithBadMemoryAccess() => _unsupported('Bad memory access');

void crashWithAbort() => _unsupported('abort()');

void crashWithIllegalInstruction() => _unsupported('raise(SIGILL)');

Future<void> crashInIsolate() async => _unsupported('Isolate crash');
