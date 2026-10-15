package com.kuronami.arswarptomapx.compat.xaero;

/**
 * Ars Warp to Map のウェイポイント色（固定 RGB）→ Xaero ウェイポイント色への変換（純粋関数・JUnit 対象）。
 *
 * <p>Xaero の {@code WaypointColor} は 21 色の enum で、うち染料由来の対応は
 * {@code WaypointColor.fromDye(DyeColor)}（javap 一次確認・xaeros-minimap
 * neoforge-1.21.1-26.4.2 の switch map 逆アセンブル）が公式表。本 MOD は
 * Reflection で動くため DyeColor を渡せず、その対応表を固定配列で模倣する。</p>
 *
 * <p>色空間は「染料 index 0–15」（{@code DyeColor.ordinal()} 順・
 * XAERO_NOTES のチャット共有書式 colorIdx と同じ空間）を経由する:</p>
 *
 * <pre>
 * 固定 RGB ──最近接──▶ 染料 index (0-15) ──fromDye 対応表──▶ WaypointColor enum index
 * </pre>
 */
public final class DyeColors {

    private DyeColors() {
    }

    /**
     * 染料 16 色の代表 RGB（0xRRGGBB）。添字 = 染料 index（DyeColor ordinal 順）。
     * 出典: vanilla {@code DyeColor} のテキスト色定義（textureDiffuseColors 相当の公知値）。
     */
    private static final int[] DYE_RGB = {
            0xF9FFFE, // 0  white
            0xF9801D, // 1  orange
            0xC74EBD, // 2  magenta
            0x3AB3DA, // 3  light_blue
            0xFED83D, // 4  yellow
            0x80C71F, // 5  lime
            0xF38BAA, // 6  pink
            0x474F52, // 7  gray
            0x9D9D97, // 8  light_gray
            0x169C9C, // 9  cyan
            0x8932B8, // 10 purple
            0x3C44AA, // 11 blue
            0x835432, // 12 brown
            0x5E7C16, // 13 green
            0xB02E26, // 14 red
            0x1D1D21, // 15 black
    };

    /**
     * 染料 index → Xaero {@code WaypointColor} enum index の対応表
     * （{@code WaypointColor.fromDye} の switch map 実測による固定写像）。
     * 添字 = 染料 index。enum index は {@code values()[idx]} の添字
     * （0=BLACK … 15=WHITE … 20=BROWN）で、{@code fromIndex(int)} と同じ空間。
     */
    private static final int[] DYE_INDEX_TO_WAYPOINT_COLOR_INDEX = {
            15, // dye WHITE      -> WaypointColor.WHITE
            6,  // dye ORANGE     -> WaypointColor.GOLD
            16, // dye MAGENTA    -> WaypointColor.MAGENTA
            17, // dye LIGHT_BLUE -> WaypointColor.LIGHT_BLUE
            14, // dye YELLOW     -> WaypointColor.YELLOW
            18, // dye LIME       -> WaypointColor.LIME
            19, // dye PINK       -> WaypointColor.PINK
            8,  // dye GRAY       -> WaypointColor.DARK_GRAY
            7,  // dye LIGHT_GRAY -> WaypointColor.GRAY
            11, // dye CYAN       -> WaypointColor.AQUA
            13, // dye PURPLE     -> WaypointColor.PURPLE
            9,  // dye BLUE       -> WaypointColor.BLUE
            20, // dye BROWN      -> WaypointColor.BROWN
            10, // dye GREEN      -> WaypointColor.GREEN
            12, // dye RED        -> WaypointColor.RED
            0,  // dye BLACK      -> WaypointColor.BLACK
    };

    /**
     * RGB に最も近い染料色の index を返す（距離は RGB 各チャンネル差の二乗和）。
     * アルファ成分が混ざっていても下位 24bit だけで判定する。
     *
     * @param rgb 任意の int（0xRRGGBB 部分のみ使用）
     * @return 染料 index（0–15・必ず範囲内）
     */
    public static int nearestDyeIndex(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < DYE_RGB.length; i++) {
            long dr = r - ((DYE_RGB[i] >> 16) & 0xFF);
            long dg = g - ((DYE_RGB[i] >> 8) & 0xFF);
            long db = b - (DYE_RGB[i] & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /**
     * 染料 index を Xaero {@code WaypointColor.fromIndex(int)} 用の enum index へ変換する。
     *
     * @param dyeIndex 染料 index（0–15・範囲外はクランプ）
     * @return WaypointColor enum index（0–20・必ず範囲内）
     */
    public static int dyeIndexToWaypointColorIndex(int dyeIndex) {
        int clamped = Math.max(0, Math.min(DYE_INDEX_TO_WAYPOINT_COLOR_INDEX.length - 1, dyeIndex));
        return DYE_INDEX_TO_WAYPOINT_COLOR_INDEX[clamped];
    }
}
