<?php

declare(strict_types=1);

namespace Highlight\SDK\Common;

/**
 * Represents the priority of a log message.
 * The priority can be one of LOW, NORMAL, MEDIUM, or HIGH.
 */
final class Priority
{
    private int $difference;

    public const LOW = 0;
    public const NORMAL = 1;
    public const MEDIUM = 2;
    public const HIGH = 3;

    public function __construct(int $difference)
    {
        $this->difference = $difference;
    }

    public function difference(): int
    {
        return $this->difference;
    }

    public static function LOW(): self
    {
        return new Priority(self::LOW);
    }

    public static function NORMAL(): self
    {
        return new Priority(self::NORMAL);
    }

    public static function MEDIUM(): self
    {
        return new Priority(self::MEDIUM);
    }

    public static function HIGH(): self
    {
        return new Priority(self::HIGH);
    }    
}
