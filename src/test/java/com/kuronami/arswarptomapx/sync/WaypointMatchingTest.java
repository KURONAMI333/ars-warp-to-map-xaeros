package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「この MOD が入れたウェイポイントか」の判定（WaypointMatching）のテスト。
 * 利用者が手で作ったウェイポイントを、名前だけ・座標だけの一致で消してしまわないことを守る。
 */
class WaypointMatchingTest {

    private static PointSnapshot recorded(String name, int x, int y, int z) {
        return new PointSnapshot(UUID.nameUUIDFromBytes("r".getBytes()), name, x, y, z,
                "minecraft:overworld", 0xFF0000);
    }

    @Test
    void sameNameAndCoordinatesMatch() {
        assertTrue(WaypointMatching.samePoint(recorded("Base", 10, 64, -20), "Base", 10, 64, -20));
    }

    @Test
    void sameCoordinatesDifferentNameDoNotMatch() {
        // 利用者が同名座標の近くに別名のウェイポイントを置いたケース
        assertFalse(WaypointMatching.samePoint(recorded("Base", 10, 64, -20), "My Base", 10, 64, -20));
    }

    @Test
    void sameNameDifferentCoordinatesDoNotMatch() {
        // 利用者が同じ名前のウェイポイントを別の場所に置いたケース（改名じゃなく新規）
        assertFalse(WaypointMatching.samePoint(recorded("Base", 10, 64, -20), "Base", 11, 64, -20));
    }

    @Test
    void nullRecordedNeverMatches() {
        assertFalse(WaypointMatching.samePoint(null, "Base", 10, 64, -20));
    }

    @Test
    void nullNameNeverMatches() {
        assertFalse(WaypointMatching.samePoint(recorded("Base", 10, 64, -20), null, 10, 64, -20));
    }
}
