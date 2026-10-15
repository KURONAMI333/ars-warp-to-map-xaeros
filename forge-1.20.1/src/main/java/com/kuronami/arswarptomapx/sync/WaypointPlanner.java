package com.kuronami.arswarptomapx.sync;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 巻物の行き先一覧を「あるべきウェイポイント集合」へ変換する純粋関数。
 *
 * <p>行き先が未設定・壊れた巻物はこの層へ到達する前に読み取り側で弾かれている
 * （{@code ArsScrollReader} が null を返す）。ここでは次元が空のものだけを
 * 念のため除外し、同一行き先を 1 本へ集約する。集約時は最初に現れた巻物の名前を
 * 採用する（インベントリ順で決定的）。</p>
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
            if (d == null || isBlank(d.dimensionId())) {
                continue;
            }
            UUID id = DestinationKey.of(d.dimensionId(), d.x(), d.y(), d.z());
            desired.putIfAbsent(id, new PointSnapshot(
                    id, d.name(), d.x(), d.y(), d.z(), d.dimensionId(), WAYPOINT_RGB));
        }
        return desired;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
