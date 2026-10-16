package com.kuronami.arswarptomapx.sync;

import com.kuronami.arswarptomapx.compat.xaero.DyeColors;
import com.kuronami.arswarptomapx.compat.xaero.Initials;

/**
 * 地図 MOD 内の実体が「この MOD が入れたもの」かを判定する純粋関数。
 * Xaero's Minimap の実体（名前・座標・略号・色）と記録（{@link PointSnapshot}）を
 * 突き合わせる基準を 1 箇所に集め、利用者が手で作ったウェイポイントを
 * 消さない・作り直さないことを守る。MC / Xaero の型に依存しない。
 */
public final class WaypointMatching {

    private WaypointMatching() {
    }

    /**
     * 記録と実体が同じ地点か（名前・座標・略号・色がすべて一致）。
     *
     * <p>UUID は地図 MOD 側の実体からは読めないため、この MOD が作成時に与える属性
     * （名前・座標と、名前から導く略号・RGB から導く色）の一致で自作物を識別する。
     * 利用者が同名同座標に手で作ったウェイポイントは略号や色が異なることが多く、
     * これを取り違えて消してしまわないようにする。名前だけ・座標だけの一致では
     * 一致とみなさない。</p>
     *
     * @param recorded   記録側のスナップショット（null なら常に false）
     * @param name       実体の名前
     * @param x          実体のブロック座標 X
     * @param y          実体のブロック座標 Y
     * @param z          実体のブロック座標 Z
     * @param initials   実体の略号（{@code getInitials()}・null は {@code ""}）
     * @param colorIndex 実体の色（{@code getColor()} = {@code WaypointColor.ordinal()}）
     */
    public static boolean samePoint(PointSnapshot recorded, String name, int x, int y, int z,
                                    String initials, int colorIndex) {
        if (recorded == null || name == null) {
            return false;
        }
        if (recorded.x() != x || recorded.y() != y || recorded.z() != z) {
            return false;
        }
        if (!recorded.name().equals(name)) {
            return false;
        }
        if (!Initials.of(recorded.name()).equals(initials == null ? "" : initials)) {
            return false;
        }
        return expectedColorIndex(recorded) == colorIndex;
    }

    /**
     * 記録から、この MOD がその地点を地図へ入れる時の色 index を導く。
     * 作成規則（{@code DyeColors.nearestDyeIndex} → {@code dyeIndexToWaypointColorIndex}）と
     * 同じ写像を使う。{@code WaypointColor.ordinal()} と同じ空間（{@code getColor()} の戻り値）。
     *
     * @param recorded 記録側のスナップショット
     * @return {@code WaypointColor} enum index（0–20・必ず範囲内）
     */
    public static int expectedColorIndex(PointSnapshot recorded) {
        return DyeColors.dyeIndexToWaypointColorIndex(DyeColors.nearestDyeIndex(recorded.rgb()));
    }
}
