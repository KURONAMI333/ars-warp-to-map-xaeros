package com.kuronami.arswarptomapx.sync;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * クライアント側の「前回反映状態 vs 現在状態」比較のための純粋関数群。
 * Ars Nouveau / Minecraft の型に依存しない（JUnit が MC クラスパス無しで回る）。
 */
public final class LocationStateOps {

    private LocationStateOps() {
    }

    /**
     * 比較用に状態を正規化する。
     * キー昇順の TreeMap へ詰め替え（順序を決定論的に）＋ null 値の除去。
     *
     * @param state 地点集合（null 許容・null は空として扱う）
     * @return 正規化された不変マップ
     */
    public static Map<UUID, PointSnapshot> normalize(Map<UUID, PointSnapshot> state) {
        TreeMap<UUID, PointSnapshot> sorted = new TreeMap<>();
        if (state != null) {
            for (Map.Entry<UUID, PointSnapshot> entry : state.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sorted.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return java.util.Collections.unmodifiableSortedMap(sorted);
    }

    /**
     * 前回反映した状態と現在の状態が異なるか。
     * 順序の違いは同一とみなし、内容の差分のみを検出する。
     *
     * @param previous 前回反映分（初回は null）
     * @param current      現在の状態
     * @return 反映が必要なら true
     */
    public static boolean stateChanged(Map<UUID, PointSnapshot> previous,
                                       Map<UUID, PointSnapshot> current) {
        return !normalize(current).equals(normalize(previous));
    }

    /**
     * 指定次元の地点だけを抜き出す（挿入順保持・null 安全）。
     * {@code getCurrentWaypointSet()} は現在次元のセットしか返さない。
     * そのためここで現在次元分だけを抽出し、他次元分は呼び出し元の desired 内に保留される。
     *
     * @param state       地点集合（null 許容）
     * @param dimensionId 抽出する次元 ID（例: {@code minecraft:overworld}・null 許容）
     * @return フィルタされたマップ（新規インスタンス）
     */
    public static Map<UUID, PointSnapshot> filterByDimension(Map<UUID, PointSnapshot> state, String dimensionId) {
        java.util.LinkedHashMap<UUID, PointSnapshot> result = new java.util.LinkedHashMap<>();
        if (state != null && dimensionId != null) {
            for (Map.Entry<UUID, PointSnapshot> entry : state.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && dimensionId.equals(entry.getValue().dimensionId())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * クライアント側で保留分を適用すべきか。
     *
     * <p>最新の走査がまだ未反映（{@code dirty}）か、プレイヤーの次元が「最後に適用した次元」から
     * 変わった時に true。Xaero の {@code getCurrentWaypointSet()} は現在次元のセットしか返さず、
     * インベントリの走査は次元移動を通知しないため、次元が変わったら保留分をその次元へ適用し直す必要がある。</p>
     *
     * @param dirty              走査したがまだ反映していないデータがあるか
     * @param appliedDimensionId 最後に反映した次元 ID（未反映なら null）
     * @param currentDimensionId プレイヤーの現在次元 ID（レベル未ロードなら null）
     * @return 適用処理を走らせるべきなら true
     */
    public static boolean shouldApply(boolean dirty, String appliedDimensionId, String currentDimensionId) {
        if (currentDimensionId == null) {
            return false;
        }
        return dirty || !currentDimensionId.equals(appliedDimensionId);
    }
}
