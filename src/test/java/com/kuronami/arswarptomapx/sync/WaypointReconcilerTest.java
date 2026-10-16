package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3 項突き合わせ（望ましい集合・記録・セット内の実体）のテスト。
 * 「記録はあるが実体が無い→作り直す」「記録が読めない間は同名同座標の実体を採用して重複を作らない」
 * 「記録が健全な時の未記録の同名同座標実体は利用者のものとして触らない」「利用者の実体は消さない」を守る。
 */
class WaypointReconcilerTest {

    private static UUID id(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes());
    }

    private static PointSnapshot point(String seed, String name, int x, int y, int z) {
        return new PointSnapshot(id(seed), name, x, y, z, "minecraft:overworld", 0xFF6A00);
    }

    private static WaypointReconciler.Entity entity(PointSnapshot s) {
        return new WaypointReconciler.Entity(s.name(), s.x(), s.y(), s.z());
    }

    @Test
    void recordedButMissingEntityIsRecreated() {
        // 再起動後に Xaero 側の実体が残っていない（add だけでは保存されない版）ケース
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(a.id(), a),
                List.of(),
                true);

        assertEquals(List.of(a), plan.creations());
        assertTrue(plan.removals().isEmpty());
        assertEquals(Map.of(a.id(), a), plan.record());
        assertTrue(plan.changesTheSet());
    }

    @Test
    void recordMissingButEntityMatchesIsAdoptedNoDuplicate() {
        // 記録ファイルがまだ無い（読めない）状態。セットには同名同座標の実体がある → 採用して重複を作らない
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(),
                List.of(entity(a)),
                false);

        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.removals().isEmpty());
        assertEquals(Map.of(a.id(), a), plan.record());
        assertFalse(plan.changesTheSet());
    }

    @Test
    void healthyRecordLeavesUntrackedIdenticalEntityAlone() {
        // 記録が健全に読めているのに、その地点は記録に無い。同名同座標の実体は利用者の手作り。
        // 消さず・自分の分も作らず（重複回避）・記録にも載せない。
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(),
                List.of(entity(a)),
                true);

        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.removals().isEmpty());
        assertTrue(plan.record().isEmpty());
        assertFalse(plan.changesTheSet());
    }

    @Test
    void partialFailureThenRetryDoesNotDuplicate() {
        // 前回 A を作った直後に例外 → 記録が未保存のまま、セットには A の実体だけある。
        // 記録がまだ読めないので A を採用し、B だけ作る（重複しない）
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        PointSnapshot b = point("b", "Mine", 30, 70, 5);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a, b.id(), b),
                Map.of(),
                List.of(entity(a)),
                false);

        assertEquals(List.of(b), plan.creations()); // A は採用、B だけ作る
        assertTrue(plan.removals().isEmpty());
        assertEquals(Map.of(a.id(), a, b.id(), b), plan.record());
    }

    @Test
    void removedDestinationDropsRecordAndDeletesEntity() {
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(),
                Map.of(a.id(), a),
                List.of(entity(a)),
                true);

        assertEquals(List.of(0), plan.removals());
        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.record().isEmpty());
    }

    @Test
    void removedDestinationWithoutEntityJustDropsRecord() {
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(),
                Map.of(a.id(), a),
                List.of(),
                true);

        assertTrue(plan.removals().isEmpty());
        assertTrue(plan.record().isEmpty());
        assertFalse(plan.changesTheSet());
    }

    @Test
    void movedDestinationReplacesOldEntity() {
        PointSnapshot old = point("a", "Base", 10, 64, -20);
        PointSnapshot moved = new PointSnapshot(old.id(), "Base", 100, 64, -200,
                old.dimensionId(), old.rgb());
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(moved.id(), moved),
                Map.of(old.id(), old),
                List.of(entity(old)),
                true);

        assertEquals(List.of(0), plan.removals());      // 旧座標の実体を消す
        assertEquals(List.of(moved), plan.creations()); // 新座標へ作り直す
        assertEquals(Map.of(moved.id(), moved), plan.record());
    }

    @Test
    void userWaypointIsLeftUntouched() {
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Entity user = new WaypointReconciler.Entity("My Spot", 1, 2, 3);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(a.id(), a),
                List.of(entity(a), user),
                true);

        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.removals().isEmpty()); // user の位置(1)は消さない
        assertEquals(Map.of(a.id(), a), plan.record());
        assertFalse(plan.changesTheSet());
    }

    @Test
    void renameDoesNotAdoptUserWaypointWithSameNewName() {
        // 巻物を改名した（記録は「Base」・望ましい集合は「Home」）。同じ場所に利用者が手で作った
        // 「Home」がある。id の有無だけで取り込むと、この手作り地点を自分のものと取り違えて
        // 後に消してしまう。記録の内容と一致しない実体は採用せず、利用者の実体も消さない。
        PointSnapshot recorded = point("a", "Base", 10, 64, -20);
        PointSnapshot renamed = new PointSnapshot(recorded.id(), "Home", 10, 64, -20,
                recorded.dimensionId(), recorded.rgb());
        WaypointReconciler.Entity user = new WaypointReconciler.Entity("Home", 10, 64, -20);

        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(renamed.id(), renamed),
                Map.of(recorded.id(), recorded),
                List.of(user),
                true);

        assertTrue(plan.creations().isEmpty()); // 手作りの「Home」は取り込まない
        assertTrue(plan.removals().isEmpty());  // 座標が一致する利用者の実体も消さない
        assertTrue(plan.record().isEmpty());    // 自分の管理分として記録にも載せない
        assertFalse(plan.changesTheSet());
    }

    @Test
    void renameReplacesOwnOldEntity() {
        // 手作り地点が無い普通の改名。旧名の自分の実体を消し、新名で作り直す。
        PointSnapshot recorded = point("a", "Base", 10, 64, -20);
        PointSnapshot renamed = new PointSnapshot(recorded.id(), "Home", 10, 64, -20,
                recorded.dimensionId(), recorded.rgb());

        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(renamed.id(), renamed),
                Map.of(recorded.id(), recorded),
                List.of(entity(recorded)),
                true);

        assertEquals(List.of(0), plan.removals());       // 旧名の実体を消す
        assertEquals(List.of(renamed), plan.creations()); // 新名で作り直す
        assertEquals(Map.of(renamed.id(), renamed), plan.record());
    }

    @Test
    void cleanStateIsNoOp() {
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(a.id(), a),
                List.of(entity(a)),
                true);

        assertTrue(plan.removals().isEmpty());
        assertTrue(plan.creations().isEmpty());
        assertFalse(plan.changesTheSet());
    }

    @Test
    void nullNamedEntityDoesNotStopReconcile() {
        // 他 MOD が API 経由で入れた名前 null のウェイポイントが混ざっても NPE で同期が止まらない。
        // null 名は "" として扱い、その実体は照合対象になる（ここでは座標が違うので一致しない）。
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Entity unnamed = new WaypointReconciler.Entity(null, 1, 2, 3);
        assertEquals("", unnamed.name());
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(a.id(), a),
                List.of(unnamed, entity(a)),
                true);

        assertTrue(plan.removals().isEmpty());
        assertTrue(plan.creations().isEmpty());
        assertEquals(Map.of(a.id(), a), plan.record());
        assertFalse(plan.changesTheSet());
    }
}
