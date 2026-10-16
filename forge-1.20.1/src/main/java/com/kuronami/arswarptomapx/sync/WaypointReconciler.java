package com.kuronami.arswarptomapx.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 「望ましい行き先の集合」「この MOD が入れた記録」「地図 MOD のセットに実在する実体」の 3 つを
 * 突き合わせ、実際に加えるべき操作を決める純粋関数。
 *
 * <p>Xaero's Minimap は {@code WaypointSet.add} だけではディスクへ保存しないことがあり、
 * 再起動後のセットに実体が残っていない場合がある。そのため「作成済み」を記録だけでは判定せず、
 * <b>記録と名前・座標が一致する実体が今のセットにあるもの</b>に限る。実体が無ければ作り直す。</p>
 *
 * <p>記録が読めていない（ファイル不在・破損）間は、実体だけあって記録が無い場合にその実体を
 * 自分のものとして採用し記録に載せる（重複を作らない）。これに対し記録が健全に読めている時は、
 * 記録の内容と完全に一致する地点（同じ id で名前・座標なども同一）だけを採用する。巻物を改名・
 * 移動した結果、記録に無い同名同座標の実体（利用者が手で作ったもの）と一致しても取り込まず、
 * 消さず・採用もせず・記録にも載せない。望ましい地点に対応する実体が無ければ作り直す。</p>
 *
 * <p>MC / Xaero の型に依存しない（JUnit が MC クラスパス無しで回る）。実体は
 * {@link Entity}（名前と座標のみ）で受け取り、削除対象は実体リストの位置で返す。</p>
 */
public final class WaypointReconciler {

    private WaypointReconciler() {
    }

    /** セット内の実体 1 件の、判定に必要な情報だけを持つ値。 */
    public record Entity(String name, int x, int y, int z) {

        /** 他 MOD が API で入れた名前 null の実体でも同期を止めない（{@code ""} として扱う）。 */
        public Entity {
            name = name == null ? "" : name;
        }

        /** 記録・望ましい地点と同じ地点か（名前と座標が一致。判定則は {@link WaypointMatching} と同一）。 */
        public boolean samePointAs(PointSnapshot snapshot) {
            return WaypointMatching.samePoint(snapshot, name, x, y, z);
        }
    }

    /**
     * 適用計画。
     *
     * @param removals  実体リスト（reconcile に渡した {@code present}）のうち取り除くべき位置
     * @param creations 新規作成すべき地点（id 昇順）
     * @param record    適用後にこの次元について記録へ載るべき内容（この MOD が管理する地点だけ）
     */
    public record Plan(List<Integer> removals, List<PointSnapshot> creations,
                       Map<UUID, PointSnapshot> record) {

        public Plan {
            removals = List.copyOf(removals);
            creations = List.copyOf(creations);
            record = Collections.unmodifiableMap(new LinkedHashMap<>(record));
        }

        /** セットへの追加・削除を伴うか（これが false なら地図 MOD のデータは変わらない）。 */
        public boolean changesTheSet() {
            return !removals.isEmpty() || !creations.isEmpty();
        }
    }

    /**
     * 適用計画を立てる。
     *
     * @param desired       この次元で望ましい地点集合（null 禁止・空は全削除が正）
     * @param recorded      この次元について記録に残っている地点集合（null 禁止）
     * @param present       地図 MOD のセットに実在する実体（null 禁止・順序は呼び出し側の実体リストと一致）
     * @param recordHealthy 記録ファイルが健全に読めたなら true（不在・破損時は false）
     * @return 削除する実体の位置・作成する地点・適用後の記録内容
     */
    public static Plan reconcile(Map<UUID, PointSnapshot> desired,
                                 Map<UUID, PointSnapshot> recorded,
                                 List<Entity> present,
                                 boolean recordHealthy) {
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(recorded, "recorded");
        Objects.requireNonNull(present, "present");

        boolean[] claimed = new boolean[present.size()];
        List<Integer> removals = new ArrayList<>();
        List<PointSnapshot> creations = new ArrayList<>();
        Map<UUID, PointSnapshot> record = new LinkedHashMap<>();

        // 1) 望ましい各地点: 同名同座標の実体があれば採用（新規作成しない）、無ければ作成する。
        //    ただし記録が健全に読めている時は、記録の内容と完全に一致する地点だけを採用する。
        //    巻物を改名・移動した後に、たまたま同名同座標の手作り実体があっても取り違えて
        //    消してしまわないようにする（利用者が手で作った実体を自分のものとみなさない）。
        List<PointSnapshot> desiredSorted = new ArrayList<>(desired.values());
        desiredSorted.sort(Comparator.comparing(PointSnapshot::id));
        for (PointSnapshot snapshot : desiredSorted) {
            boolean adoptable = !recordHealthy || snapshot.equals(recorded.get(snapshot.id()));
            int index = findMatching(present, claimed, snapshot);
            if (index < 0) {
                creations.add(snapshot);
                record.put(snapshot.id(), snapshot);
            } else if (adoptable) {
                claimed[index] = true;
                record.put(snapshot.id(), snapshot);
            }
            // index >= 0 かつ adoptable でない: 利用者が手で作った実体。消さず・採用もせず・記録にも載せない
        }

        // 2) 記録のうち望ましくないもの（内容が変わった・望ましい集合から消えた）:
        //    同名同座標の実体があれば消す。記録からは常に落ちる（管理分だけが 1) で載る）。
        List<UUID> recordedIds = new ArrayList<>(recorded.keySet());
        recordedIds.sort(Comparator.naturalOrder());
        for (UUID id : recordedIds) {
            PointSnapshot old = recorded.get(id);
            PointSnapshot want = desired.get(id);
            if (want != null && want.equals(old)) {
                continue; // 記録は最新。1) が実体を採用済み（またはセット内に無く作り直し予定）
            }
            int index = findMatching(present, claimed, old);
            if (index >= 0) {
                claimed[index] = true;
                removals.add(index);
            }
        }

        return new Plan(removals, creations, record);
    }

    /** 未使用の実体から {@code snapshot} と同名同座標のものを探し、その位置を返す（無ければ -1）。 */
    private static int findMatching(List<Entity> present, boolean[] claimed, PointSnapshot snapshot) {
        if (snapshot == null) {
            return -1;
        }
        for (int i = 0; i < present.size(); i++) {
            if (!claimed[i] && present.get(i).samePointAs(snapshot)) {
                return i;
            }
        }
        return -1;
    }
}
