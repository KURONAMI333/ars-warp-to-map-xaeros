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
 * 「記録はあるが実体が無い→作り直す」「記録が無いが同名同座標の実体がある→採用して重複を作らない」
 * 「途中で例外→次回重複しない」「利用者の実体は消さない」を守る。
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
                List.of());

        assertEquals(List.of(a), plan.creations());
        assertTrue(plan.removals().isEmpty());
        assertEquals(Map.of(a.id(), a), plan.record());
        assertTrue(plan.changesTheSet());
    }

    @Test
    void recordMissingButEntityMatchesIsAdoptedNoDuplicate() {
        // 記録キーのずれ・破損で記録が空。セットには同名同座標の実体がある
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(),
                List.of(entity(a)));

        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.removals().isEmpty());
        assertEquals(Map.of(a.id(), a), plan.record());
        assertFalse(plan.changesTheSet());
    }

    @Test
    void partialFailureThenRetryDoesNotDuplicate() {
        // 前回 A を作った直後に例外 → 記録は未更新のまま、セットには A の実体だけある
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        PointSnapshot b = point("b", "Mine", 30, 70, 5);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a, b.id(), b),
                Map.of(),
                List.of(entity(a)));

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
                List.of(entity(a)));

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
                List.of());

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
                List.of(entity(old)));

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
                List.of(entity(a), user));

        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.removals().isEmpty()); // user の位置(1)は消さない
        assertEquals(Map.of(a.id(), a), plan.record());
        assertFalse(plan.changesTheSet());
    }

    @Test
    void cleanStateIsNoOp() {
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(a.id(), a),
                List.of(entity(a)));

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
                List.of(unnamed, entity(a)));

        assertTrue(plan.removals().isEmpty());
        assertTrue(plan.creations().isEmpty());
        assertEquals(Map.of(a.id(), a), plan.record());
        assertFalse(plan.changesTheSet());
    }
}
