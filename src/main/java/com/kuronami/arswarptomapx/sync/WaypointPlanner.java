package com.kuronami.arswarptomapx.sync;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 巻物の行き先一覧を「あるべきウェイポイント集合」へ変換する純粋関数。
 *
 * <p>行き先が未設定・壊れた巻物はこの層へ到達する前に読み取り側で弾かれている
 * （{@code ArsScrollReader} が null を返す）。ここでは次元 ID が ResourceLocation として
 * 解釈できないものを念のため除外し（{@link DimensionId}）、同一行き先を 1 本へ集約する。
 * 集約時は名前が辞書順で最小のものを採用する（インベントリのスロット順に依存せず決定的）。</p>
 */
public final class WaypointPlanner {

    /**
     * ウェイポイント色（0xRRGGBB）。Ars Nouveau の紫系。行き先ごと・巻物種別ごとには
     * 変えない（同一行き先は 1 本に集約するため、種別で色分けすると衝突する）。
     */
    public static final int WAYPOINT_RGB = 0x8A2BE2;

    private WaypointPlanner() {
    }

    /**
     * @param destinations 巻物から読み出した行き先（null 可・空可）
     * @return 行き先キー → ウェイポイントのマップ。挿入順は入力順で決定的
     */
    public static Map<UUID, PointSnapshot> plan(List<ScrollDestination> destinations) {
        Map<UUID, PointSnapshot> desired = new LinkedHashMap<>();
        if (destinations == null) {
            return desired;
        }
        for (ScrollDestination d : destinations) {
            if (d == null || !DimensionId.isWellFormed(d.dimensionId())) {
                continue;
            }
            UUID id = DestinationKey.of(d.dimensionId(), d.x(), d.y(), d.z());
            PointSnapshot candidate = new PointSnapshot(
                    id, d.name(), d.x(), d.y(), d.z(), d.dimensionId(), WAYPOINT_RGB);
            PointSnapshot existing = desired.get(id);
            if (existing == null) {
                desired.put(id, candidate);
            } else if (candidate.name().compareTo(existing.name()) < 0) {
                // 同一行き先の巻物が複数ある時は辞書順で最小の名前を採用する
                desired.put(id, candidate);
            }
        }
        return desired;
    }
}
