<?php

namespace Highlight\SDK;

use Highlight\SDK\Highlight;
use Highlight\SDK\Common\Record\HighlightLogRecord;
use Highlight\SDK\Common\HighlightAttributes;
use OpenTelemetry\API\Logs\LogRecord;
use OpenTelemetry\SDK\Logs\Logger;

class HighlightLogger {

    private Logger $logger;

    public function __construct(Highlight $highlight) {
        $openTelemetry = $highlight->getOpenTelemetry();
		$this->logger = $openTelemetry->getLogger("highlight-php");
    }

    public function process(HighlightLogRecord $record): void {
        $severity = $record->getSeverity();
        $logRecord = new LogRecord($record->getMessage());

        // OpenTelemetry expects nanoseconds since the unix epoch; format('Uu')
        // yields seconds+microseconds, so multiplying by 1000 gives nanos.
        $logRecord->setTimestamp((int) $record->getTimeOccurred()->format('Uu') * 1000)
        ->setSeverityNumber($severity->id())
        ->setSeverityText($severity->text())
        ->setAttributes($record->getAttributes()->toArray());

        if ($record->hasUserSession()) {
            $logRecord->setAttribute(HighlightAttributes::HIGHLIGHT_SESSION_ID, $record->getUserSession()->sessionId());
        }

        if ($record->hasRequestId()) {
            $logRecord->setAttribute(HighlightAttributes::HIGHLIGHT_TRACE_ID, $record->getRequestId());
        }

        $this->logger->emit($logRecord);
    }
}
