package com.kuronami.arswarptomapx.compat.xaero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 染料色最近接マッチングと Xaero WaypointColor 対応表の単体テスト。
 * MC クラスパス無しで完結する（DyeColors が純粋関数であることの回帰ガード）。
 */
class DyeColorsTest {

    @Test
    void pureWhiteMatchesWhite() {
        assertEquals(0, DyeColors.nearestDyeIndex(0xF9FFFE));
    }

    @Test
    void pureBlackMatchesBlack() {
        assertEquals(15, DyeColors.nearestDyeIndex(0x1D1D21));
    }

    @Test
    void strongRedMatchesRed() {
        assertEquals(14, DyeColors.nearestDyeIndex(0xB02E26));
        // 暗めの赤も染料赤に最近接
        assertEquals(14, DyeColors.nearestDyeIndex(0x8B0000));
    }

    @Test
    void orangeFamilyMatchesOrange() {
        assertEquals(1, DyeColors.nearestDyeIndex(0xFFA500));
    }

    @Test
    void alphaBitsAreIgnored() {
        // アルファ付きでも RGB 部分だけで判定される
        int rgb = 0x474F52;
        assertEquals(DyeColors.nearestDyeIndex(rgb), DyeColors.nearestDyeIndex(0xAA000000 | rgb));
    }

    @Test
    void resultIsAlwaysInRange() {
        // 極端な値・境界値でも必ず 0-15 に収まる
        for (int rgb : new int[]{0x000000, 0xFFFFFF, 0x123456, 0xABCDEF, 1, -1}) {
            int idx = DyeColors.nearestDyeIndex(rgb);
            assertTrue(idx >= 0 && idx <= 15, "index out of range for " + Integer.toHexString(rgb) + ": " + idx);
        }
    }

    @Test
    void dyeToEnumTableIsInjectiveAndComplete() {
        // 対応表は染料 16 色を重複なくカバーし、値は enum index 空間 (0-20) に収まる
        boolean[] seen = new boolean[16];
        for (int dye = 0; dye < 16; dye++) {
            int mapped = DyeColors.dyeIndexToWaypointColorIndex(dye);
            assertTrue(mapped >= 0 && mapped <= 20, "enum index out of range: " + mapped);
            assertTrue(!seen[dye], "dye index used twice: " + dye); // clamp 前の全添字が一意に参照されることのガード
            seen[dye] = true;
        }
    }

    @Test
    void whiteAndBlackMapToExpectedEnums() {
        // fromDye 実測対応: WHITE->WHITE(enum idx 15), BLACK->BLACK(enum idx 0)
        assertEquals(15, DyeColors.dyeIndexToWaypointColorIndex(0));
        assertEquals(0, DyeColors.dyeIndexToWaypointColorIndex(15));
    }

    @Test
    void graySwapIsFaithful() {
        // fromDye 実測対応: GRAY染料->DARK_GRAY(8), LIGHT_GRAY染料->GRAY(7)
        assertEquals(8, DyeColors.dyeIndexToWaypointColorIndex(7));
        assertEquals(7, DyeColors.dyeIndexToWaypointColorIndex(8));
    }

    @Test
    void clampsOutOfRangeInput() {
        assertEquals(DyeColors.dyeIndexToWaypointColorIndex(0), DyeColors.dyeIndexToWaypointColorIndex(-5));
        assertEquals(DyeColors.dyeIndexToWaypointColorIndex(15), DyeColors.dyeIndexToWaypointColorIndex(99));
    }
}
