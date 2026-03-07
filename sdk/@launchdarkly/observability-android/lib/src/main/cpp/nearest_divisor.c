#include "nearest_divisor.h"

int nearest_divisor(int value, int preferred, int rangeLo, int rangeHi) {
    if (value <= 0) return preferred;
    if (preferred >= rangeLo && preferred <= rangeHi &&
        preferred > 0 && value % preferred == 0)
        return preferred;

    int maxDist = rangeHi - preferred;
    if (preferred - rangeLo > maxDist) maxDist = preferred - rangeLo;
    if (maxDist <= 0) return preferred;

    for (int offset = 1; offset <= maxDist; offset++) {
        int pos = preferred + offset;
        if (pos >= rangeLo && pos <= rangeHi && pos > 0 && value % pos == 0)
            return pos;
        int neg = preferred - offset;
        if (neg >= rangeLo && neg <= rangeHi && neg > 0 && value % neg == 0)
            return neg;
    }
    return preferred;
}
