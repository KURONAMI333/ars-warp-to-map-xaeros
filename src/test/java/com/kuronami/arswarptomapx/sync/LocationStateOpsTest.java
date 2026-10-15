package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サーバー側スナップ比較（LocationStateOps）の単体テスト。
 * 「前回送信状態 vs 現在状態」の差分判定が順序非依存・null 安全であることを守る。
 */
class LocationStateOpsTest {

    private static PointSnapshot point(String idSeed, String name, int x, int z) {
        UUID id = UUID.nameUUIDFromBytes(idSeed.getBytes());
        return new PointSnapshot(id, name, x, 64, z, "minecraft:overworld", 0xFF6A00);
    }

    @Test
    void nullPreviousMeansFirstSend() {
        Map<UUID, PointSnapshot> current = Map.of(
                UUID.nameUUIDFromBytes("a".getBytes()), point("a", "Base", 0, 0));
        assertTrue(LocationStateOps.stateChanged(null, current));
    }

    @Test
    void identicalStateDoesNotSend() {
        Map<UUID, PointSnapshot> state = new HashMap<>();
        state.put(UUID.nameUUIDFromBytes("a".getBytes()), point("a", "Base", 1, 2));
        assertFalse(LocationStateOps.stateChanged(state, state));
    }

    @Test
    void insertionOrderDoesNotMatter() {
        PointSnapshot a = point("a", "Base", 0, 0);
        PointSnapshot b = point("b", "Mine", 5, 6);
        Map<UUID, PointSnapshot> first = new HashMap<>();
        first.put(a.id(), a);
        first.put(b.id(), b);
        Map<UUID, PointSnapshot> second = new HashMap<>();
        second.put(b.id(), b);
        second.put(a.id(), a);

        // 同一内容・異なる挿入順 → 送信不要
        assertFalse(LocationStateOps.stateChanged(first, second));
        assertEquals(LocationStateOps.normalize(first), LocationStateOps.normalize(second));
    }

    @Test
    void contentChangeSends() {
        PointSnapshot original = point("a", "Base", 0, 0);
        PointSnapshot renamed = new PointSnapshot(original.id(), "Home", 0, 64, 0,
                original.dimensionId(), original.rgb());
        assertTrue(LocationStateOps.stateChanged(Map.of(original.id(), original), Map.of(original.id(), renamed)));
    }

    @Test
    void addedAndRemovedPointsSend() {
        PointSnapshot a = point("a", "Base", 0, 0);
        PointSnapshot b = point("b", "Mine", 5, 6);

        assertTrue(LocationStateOps.stateChanged(Map.of(a.id(), a), Map.of(a.id(), a, b.id(), b)));
        assertTrue(LocationStateOps.stateChanged(Map.of(a.id(), a, b.id(), b), Map.of(a.id(), a)));
    }

    @Test
    void emptyToEmptyDoesNotSend() {
        assertFalse(LocationStateOps.stateChanged(Map.of(), Map.of()));
    }

    @Test
    void normalizeDropsNullEntries() {
        Map<UUID, PointSnapshot> dirty = new HashMap<>();
        PointSnapshot a = point("a", "Base", 0, 0);
        dirty.put(a.id(), a);
        dirty.put(null, a);
        dirty.put(UUID.nameUUIDFromBytes("z".getBytes()), null);

        Map<UUID, PointSnapshot> normalized = LocationStateOps.normalize(dirty);
        assertEquals(1, normalized.size());
        assertTrue(normalized.containsKey(a.id()));
    }

    @Test
    void normalizeHandlesNullState() {
        assertTrue(LocationStateOps.normalize(null).isEmpty());
    }

    @Test
    void shouldApplyWhenDirty() {
        // 未反映データがあれば、適用済み次元と同じでも走らせる
        assertTrue(LocationStateOps.shouldApply(true, "minecraft:overworld", "minecraft:overworld"));
    }

    @Test
    void shouldApplyWhenDimensionChanged() {
        // 次元が変わったら、dirty でなくても保留分を新次元へ適用し直す
        assertTrue(LocationStateOps.shouldApply(false, "minecraft:overworld", "minecraft:the_nether"));
    }

    @Test
    void shouldApplyWhenNeverApplied() {
        assertTrue(LocationStateOps.shouldApply(false, null, "minecraft:overworld"));
    }

    @Test
    void shouldNotApplyWhenCleanAndSameDimension() {
        assertFalse(LocationStateOps.shouldApply(false, "minecraft:overworld", "minecraft:overworld"));
    }

    @Test
    void shouldNotApplyWithoutLevel() {
        assertFalse(LocationStateOps.shouldApply(true, "minecraft:overworld", null));
    }
}
