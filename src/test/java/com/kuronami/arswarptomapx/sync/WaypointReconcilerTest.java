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
        return WaypointReconciler.Entity.of(s);
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
        WaypointReconciler.Entity user = new WaypointReconciler.Entity("My Spot", 1, 2, 3, "MS", 1);
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
        // 「Home」（この MOD が与える略号・色とは違う）がある。名前と座標だけで一致とみなすと、
        // この手作り地点を自分のものと取り違え、後に消してしまう。属性まで一致しない実体は
        // 採用せず、利用者の実体も消さず、自分の「Home」を作る。
        PointSnapshot recorded = point("a", "Base", 10, 64, -20);
        PointSnapshot renamed = new PointSnapshot(recorded.id(), "Home", 10, 64, -20,
                recorded.dimensionId(), recorded.rgb());
        WaypointReconciler.Entity user = new WaypointReconciler.Entity("Home", 10, 64, -20, "H", 0);

        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(renamed.id(), renamed),
                Map.of(recorded.id(), recorded),
                List.of(user),
                true);

        assertEquals(List.of(renamed), plan.creations()); // 手作りの「Home」は取り込まず自分の分を作る
        assertTrue(plan.removals().isEmpty());            // 座標が一致する利用者の実体も消さない
        assertEquals(Map.of(renamed.id(), renamed), plan.record());
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
    void removesOwnWaypointNotUserWaypointWithSameNameAndPosition() {
        // 巻物の地点を追跡中、利用者が同じ名前・同じ座標へ手作りウェイポイントを作った
        // （Xaero は先頭に挿入する）。手作り側は略号 "MG"・色 1 で、この MOD が作る
        // "G"・色 6 とは違う。巻物の地点が消える時、消すのは利用者の実体(0)ではなく自分の実体(1)。
        PointSnapshot own = point("GammaFire", "GammaFire", 40, -60, 2);
        WaypointReconciler.Entity user =
                new WaypointReconciler.Entity("GammaFire", 40, -60, 2, "MG", 1);
        WaypointReconciler.Entity mine = entity(own);

        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(),
                Map.of(own.id(), own),
                List.of(user, mine), // Xaero は利用者の実体を先頭に置く
                true);

        assertEquals(List.of(1), plan.removals()); // 利用者(0)ではなく自分(1)を消す
        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.record().isEmpty());
    }

    @Test
    void duplicateIdenticalEntitiesRemoveOnlyOne() {
        // 完全に同一の実体が 2 つ（利用者が同じ設定で複製した等）。記録から消えた時、
        // 消すのは 1 つだけ。完全一致が複数ある時はセット内で最後のものを選ぶ（利用者の
        // 手作りは先頭に置かれ、この MOD の分は末尾に足されるため）。
        PointSnapshot a = point("a", "Base", 10, 64, -20);

        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(),
                Map.of(a.id(), a),
                List.of(entity(a), entity(a)),
                true);

        assertEquals(List.of(1), plan.removals());
        assertTrue(plan.record().isEmpty());
    }

    @Test
    void identicalDuplicateRemovesLastSoUserWaypointAtHeadSurvives() {
        // 完全に同一の属性（名前・座標・略号・色）が 2 つ並ぶ時。Xaero は利用者が手で作った
        // ウェイポイントを先頭に置き、この MOD は add(..., false) で末尾へ追加する。巻物の地点が
        // 記録から消える時は末尾（この MOD の作ったもの）を消し、先頭（利用者のもの）を残す。
        PointSnapshot own = point("GammaFire", "GammaFire", 40, -60, 2);
        WaypointReconciler.Entity user = entity(own); // 利用者が同じ設定で複製した実体
        WaypointReconciler.Entity mine = entity(own);

        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(),
                Map.of(own.id(), own),
                List.of(user, mine), // 先頭が利用者・末尾がこの MOD
                true);

        assertEquals(List.of(1), plan.removals()); // 末尾(1)を消し、先頭(0)は残す
        assertTrue(plan.creations().isEmpty());
        assertTrue(plan.record().isEmpty());
    }

    @Test
    void adoptionIgnoresSameNameAndPositionWithDifferentInitialsOrColor() {
        // 記録が読めない間（recordHealthy=false）は同名同座標の実体を採用して重複を作らないが、
        // 略号か色が違う実体は利用者の手作りなので採用しない（消さず、自分の分を作る）。
        PointSnapshot a = point("a", "Base", 10, 64, -20);
        WaypointReconciler.Entity user =
                new WaypointReconciler.Entity("Base", 10, 64, -20, "BX", 0);

        WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                Map.of(a.id(), a),
                Map.of(),
                List.of(user),
                false);

        assertEquals(List.of(a), plan.creations());
        assertTrue(plan.removals().isEmpty());
        assertEquals(Map.of(a.id(), a), plan.record());
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
        WaypointReconciler.Entity unnamed = new WaypointReconciler.Entity(null, 1, 2, 3, null, 0);
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
