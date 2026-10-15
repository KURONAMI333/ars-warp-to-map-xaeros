package com.kuronami.arswarptomapx.sync;

/**
 * 地図 MOD 内の実体が「この MOD が入れたもの」かを判定する純粋関数。
 * Xaero's Minimap の実体（名前・座標しか読めない）と記録（{@link PointSnapshot}）を
 * 突き合わせる基準を 1 箇所に集め、利用者が手で作ったウェイポイントを
 * 消さない・作り直さないことを守る。MC / Xaero の型に依存しない。
 */
public final class WaypointMatching {

    private WaypointMatching() {
    }

    /**
     * 記録と実体が同じ地点か（名前と座標が一致）。
     *
     * <p>UUID は地図 MOD 側の実体からは読めないため、名前と座標の一致で自作物を識別する。
     * 名前だけ・座標だけの一致では一致とみなさない（同座標の利用者ウェイポイントを
     * 誤って消さないため）。</p>
     *
     * @param recorded 記録側のスナップショット（null なら常に false）
     * @param name     実体の名前
     * @param x        実体のブロック座標 X
     * @param y        実体のブロック座標 Y
     * @param z        実体のブロック座標 Z
     */
    public static boolean samePoint(PointSnapshot recorded, String name, int x, int y, int z) {
        if (recorded == null || name == null) {
            return false;
        }
        return recorded.x() == x
                && recorded.y() == y
                && recorded.z() == z
                && recorded.name().equals(name);
    }
}
