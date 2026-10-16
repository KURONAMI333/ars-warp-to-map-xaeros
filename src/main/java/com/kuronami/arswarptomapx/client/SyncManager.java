package com.kuronami.arswarptomapx.client;

import com.kuronami.arswarptomapx.ArsWarpToMap;
import com.kuronami.arswarptomapx.compat.xaero.XaeroBridge;
import com.kuronami.arswarptomapx.sync.LocationStateOps;
import com.kuronami.arswarptomapx.sync.PointSnapshot;
import com.kuronami.arswarptomapx.sync.RecordHealth;
import com.kuronami.arswarptomapx.sync.WaypointPlanner;
import com.kuronami.arswarptomapx.sync.WaypointRecord;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * クライアント側の同期状態機械。今インベントリにある巻物の行き先全量を「あるべき状態」
 * として保持し、Xaero's Minimap へ差分適用する。
 *
 * <p>セマンティクス: 片方向ミラー（インベントリの巻物 → ウェイポイント集合）。</p>
 *
 * <p><b>次元フィルタ（JourneyMap 版 mod-111 との機能差）</b>: Xaero の
 * {@code getCurrentWaypointSet()} は「プレイヤーが今いる次元」のセットしか返さない。
 * 他次元の地点は誤ったセットに入るため、ここで現在次元分だけを抽出して渡し、
 * 他次元分は desired 内に保留される。</p>
 *
 * <p><b>次元移動の再適用</b>: インベントリの走査は次元移動を通知しないため、適用済みの
 * 次元を {@link #appliedDimensionId} に持ち、{@code mc.level.dimension()} が変わったら
 * 保留分をその次元へ適用し直す（{@link LocationStateOps#shouldApply}）。</p>
 *
 * <p><b>自作ウェイポイントの記録</b>: Xaero へ入れた地点は {@link WaypointRecord} に残し、
 * ワールド単位で {@link RecordStore} に永続化する。削除・置換の対象をこの記録と
 * 名前・座標が一致するものだけに限り、利用者の手作りウェイポイントを消さない。</p>
 *
 * <p><b>記録の健全性</b>: ファイルが読めない・壊れた行がある・同期が例外で中断した・保存に
 * 失敗した、のいずれかでそのセッションは不健全に落ちる（{@link RecordHealth}）。不健全な間は
 * 記録に無い同名同座標の実体を取り込む（手作り地点の取り違えより重複回避を優先）。なお空に
 * なった記録ファイルは消さず空のまま残す（次セッションで健全に読めるようにする）。</p>
 *
 * <p>Xaero 側が準備できていない間に走査しても、dirty を保持したまま tick ループから
 * 再試行する（取りこぼし無し）。</p>
 */
public final class SyncManager {

    /** 今のインベントリから算出した最新のあるべき状態（全次元・未反映分を含む保留キューを兼ねる）。 */
    private static Map<UUID, PointSnapshot> desired = null;

    /** desired の未適用フラグ。 */
    private static boolean dirty = false;

    /** 最後に Xaero へ適用した次元 ID（未適用なら null）。 */
    private static String appliedDimensionId = null;

    /** この MOD が入れたウェイポイントの記録。 */
    private static WaypointRecord record = WaypointRecord.empty();

    /** 記録ファイルを健全に読めたか（不在・破損時は false）。 */
    private static boolean recordHealthy = false;

    /** 記録ファイルの読み込み元キー（ワールド識別子）。未確定なら null。 */
    private static String recordKey = null;

    private SyncManager() {
    }

    /**
     * インベントリを走査してあるべき状態を更新する（main thread・定期呼び出し）。
     * 内容が変わらなければ何もしない。
     */
    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Map<UUID, PointSnapshot> next = WaypointPlanner.plan(InventoryScrollReader.scan(mc.player));
        if (!LocationStateOps.stateChanged(desired, next)) {
            return; // 内容同一（順序違いを含む）なら Xaero に触らず済む
        }
        desired = new LinkedHashMap<>(next);
        dirty = true;
    }

    /**
     * 未適用の状態、または次元変更後の保留分を現在次元へ適用する。
     * クライアント tick から定期呼び出しされる。
     * Xaero 不在・セッション未初期化・失敗時は静かに何もせず次回再試行。
     *
     * @return 実際に何か適用したら true
     */
    public static boolean flushPending() {
        if (desired == null) {
            return false;
        }
        String dimensionId = currentDimensionId();
        if (!LocationStateOps.shouldApply(dirty, appliedDimensionId, dimensionId)) {
            return false;
        }
        String key = ensureRecordLoaded();
        if (key == null) {
            return false; // ワールド未確定 → 次回再試行（ファイルへは触らない）
        }
        Map<UUID, PointSnapshot> currentDimDesired = LocationStateOps.filterByDimension(desired, dimensionId);
        XaeroBridge.Outcome outcome = XaeroBridge.syncDimension(dimensionId, currentDimDesired, record, recordHealthy);
        if (outcome == XaeroBridge.Outcome.APPLIED) {
            dirty = false;
            appliedDimensionId = dimensionId;
            // 保存に失敗したら、そのセッションの記録は実体とずれている可能性がある → 不健全に落とす
            recordHealthy = RecordHealth.afterSave(recordHealthy, RecordStore.save(key, record));
            ArsWarpToMap.LOGGER.debug(
                    "Synced {} waypoint(s) into {} for {}",
                    currentDimDesired.size(), dimensionId, key);
            return true;
        }
        if (outcome == XaeroBridge.Outcome.FAILED) {
            // ここへ来るのは最初の書き込み後に例外で中断した場合だけ（書き込み前の例外は RETRY で来る）。
            // 記録が部分更新かもしれないので、以降このセッションは不健全扱い
            recordHealthy = RecordHealth.afterSync(recordHealthy, true);
        }
        return false;
    }

    /** dirty 状態か（テスト・デバッグ用）。 */
    public static boolean hasPending() {
        return dirty;
    }

    /**
     * ワールド接続/切断の境界でのリセット。記録はワールド単位でファイルに残っているので
     * 消さない（次セッションで読み直す）。Xaero 上のウェイポイントも永続データなので
     * 削除しに行かない。
     */
    public static void resetSession() {
        desired = null;
        dirty = false;
        appliedDimensionId = null;
        record = WaypointRecord.empty();
        recordHealthy = false;
        recordKey = null;
    }

    /**
     * 記録ファイルをこのセッションで一度だけ読む。ワールドが確定できない間は
     * null を返して次回再試行する。
     */
    private static String ensureRecordLoaded() {
        if (recordKey != null) {
            return recordKey;
        }
        String key = RecordStore.worldKey(Minecraft.getInstance());
        if (key == null) {
            return null;
        }
        RecordStore.LoadResult loaded = RecordStore.load(key);
        record = loaded.record();
        recordHealthy = loaded.healthy();
        recordKey = key;
        return recordKey;
    }

    /**
     * プレイヤーの現在次元 ID（例: {@code minecraft:overworld}）。
     * レベルが未ロードなら null。
     */
    private static String currentDimensionId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        return mc.level.dimension().location().toString();
    }
}
