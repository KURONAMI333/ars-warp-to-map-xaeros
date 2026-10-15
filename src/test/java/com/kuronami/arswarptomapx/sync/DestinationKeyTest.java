package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 行き先キーの単体テスト。MC クラスパス無しで完結する。
 */
class DestinationKeyTest {

    @Test
    void sameDestinationGivesSameKey() {
        UUID a = DestinationKey.of("minecraft:overworld", 128, 64, -512);
        UUID b = DestinationKey.of("minecraft:overworld", 128, 64, -512);
        assertEquals(a, b);
    }

    @Test
    void keyIsDeterministicAcrossCalls() {
        // プロセスを跨いでも同じ値になる必要がある（UUID.nameUUIDFromBytes ベース）
        assertEquals(
                DestinationKey.of("minecraft:overworld", 0, 70, 0),
                DestinationKey.of("minecraft:overworld", 0, 70, 0));
    }

    @Test
    void changingAnyCoordinateChangesTheKey() {
        UUID base = DestinationKey.of("minecraft:overworld", 10, 20, 30);
        assertNotEquals(base, DestinationKey.of("minecraft:overworld", 11, 20, 30));
        assertNotEquals(base, DestinationKey.of("minecraft:overworld", 10, 21, 30));
        assertNotEquals(base, DestinationKey.of("minecraft:overworld", 10, 20, 31));
    }

    @Test
    void changingDimensionChangesTheKey() {
        assertNotEquals(
                DestinationKey.of("minecraft:overworld", 1, 2, 3),
                DestinationKey.of("minecraft:the_nether", 1, 2, 3));
    }

    @Test
    void negativeCoordinatesAreDistinct() {
        assertNotEquals(
                DestinationKey.of("minecraft:overworld", -1, -1, -1),
                DestinationKey.of("minecraft:overworld", 1, 1, 1));
    }
}
