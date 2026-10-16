package com.kuronami.arswarptomapx.sync;

import com.kuronami.arswarptomapx.compat.xaero.Initials;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「この MOD が入れたウェイポイントか」の判定（WaypointMatching）のテスト。
 * 利用者が手で作ったウェイポイントを、名前・座標だけの一致で消してしまわないことを守る。
 * 名前・座標が同じでも、この MOD が作る略号・色と違えば自分のものとみなさない。
 */
class WaypointMatchingTest {

    private static PointSnapshot recorded(String name, int x, int y, int z) {
        return new PointSnapshot(UUID.nameUUIDFromBytes("r".getBytes()), name, x, y, z,
                "minecraft:overworld", 0xFF0000);
    }

    /** この MOD が {@code recorded} の地点を作る時に与える属性で一致判定する。 */
    private static boolean matches(PointSnapshot recorded, String name, int x, int y, int z) {
        return WaypointMatching.samePoint(recorded, name, x, y, z,
                Initials.of(name), WaypointMatching.expectedColorIndex(recorded));
    }

    @Test
    void sameNameCoordinatesInitialsAndColorMatch() {
        assertTrue(matches(recorded("Base", 10, 64, -20), "Base", 10, 64, -20));
    }

    @Test
    void sameCoordinatesDifferentNameDoNotMatch() {
        // 利用者が同名座標の近くに別名のウェイポイントを置いたケース
        assertFalse(matches(recorded("Base", 10, 64, -20), "My Base", 10, 64, -20));
    }

    @Test
    void sameNameDifferentCoordinatesDoNotMatch() {
        // 利用者が同じ名前のウェイポイントを別の場所に置いたケース（改名じゃなく新規）
        assertFalse(matches(recorded("Base", 10, 64, -20), "Base", 11, 64, -20));
    }

    @Test
    void sameNameAndCoordinatesButDifferentInitialsDoNotMatch() {
        // 同名同座標でも、この MOD が与える略号と違う（利用者が略号を変えた手作り）
        PointSnapshot r = recorded("Base", 10, 64, -20);
        assertFalse(WaypointMatching.samePoint(r, "Base", 10, 64, -20, "X",
                WaypointMatching.expectedColorIndex(r)));
    }

    @Test
    void sameNameAndCoordinatesButDifferentColorDoNotMatch() {
        // 同名同座標でも、この MOD が与える色と違う（利用者が色を選んだ手作り）
        PointSnapshot r = recorded("Base", 10, 64, -20);
        int expected = WaypointMatching.expectedColorIndex(r);
        int other = expected == 0 ? 1 : 0;
        assertFalse(WaypointMatching.samePoint(r, "Base", 10, 64, -20, Initials.of("Base"), other));
    }

    @Test
    void nullRecordedNeverMatches() {
        assertFalse(WaypointMatching.samePoint(null, "Base", 10, 64, -20, "B", 0));
    }

    @Test
    void nullNameNeverMatches() {
        assertFalse(WaypointMatching.samePoint(recorded("Base", 10, 64, -20), null, 10, 64, -20, "B", 0));
    }

    @Test
    void nullInitialsAreTreatedAsEmpty() {
        // 他 MOD が API で入れた略号 null の実体で NPE を出さない（"" として扱う）
        PointSnapshot r = recorded("", 10, 64, -20);
        assertTrue(WaypointMatching.samePoint(r, "", 10, 64, -20, null,
                WaypointMatching.expectedColorIndex(r)));
    }
}
