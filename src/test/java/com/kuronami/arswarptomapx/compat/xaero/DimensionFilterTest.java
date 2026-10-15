package com.kuronami.arswarptomapx.compat.xaero;

import com.kuronami.arswarptomapx.sync.LocationStateOps;
import com.kuronami.arswarptomapx.sync.PointSnapshot;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 次元フィルタ（LocationStateOps.filterByDimension）の単体テスト。
 * Xaero の「現在次元のセットしか触れない」制約を守る純粋部分の回帰ガード。
 */
class DimensionFilterTest {

    private static PointSnapshot point(String idSeed, String dim) {
        UUID id = UUID.nameUUIDFromBytes(idSeed.getBytes());
        return new PointSnapshot(id, idSeed, 1, 64, 2, dim, 0xFF0000);
    }

    @Test
    void keepsOnlyRequestedDimension() {
        Map<UUID, PointSnapshot> state = new HashMap<>();
        PointSnapshot overworld = point("ow", "minecraft:overworld");
        PointSnapshot nether = point("ne", "minecraft:the_nether");
        state.put(overworld.id(), overworld);
        state.put(nether.id(), nether);

        Map<UUID, PointSnapshot> filtered = LocationStateOps.filterByDimension(state, "minecraft:overworld");

        assertEquals(1, filtered.size());
        assertTrue(filtered.containsKey(overworld.id()));
    }

    @Test
    void emptyStateYieldsEmptyResult() {
        assertTrue(LocationStateOps.filterByDimension(Map.of(), "minecraft:overworld").isEmpty());
    }

    @Test
    void nullStateYieldsEmptyResult() {
        assertTrue(LocationStateOps.filterByDimension(null, "minecraft:overworld").isEmpty());
    }

    @Test
    void nullDimensionYieldsEmptyResult() {
        Map<UUID, PointSnapshot> state = new HashMap<>();
        PointSnapshot p = point("ow", "minecraft:overworld");
        state.put(p.id(), p);
        assertTrue(LocationStateOps.filterByDimension(state, null).isEmpty());
    }

    @Test
    void unknownDimensionYieldsEmptyResult() {
        Map<UUID, PointSnapshot> state = new HashMap<>();
        PointSnapshot p = point("ow", "minecraft:overworld");
        state.put(p.id(), p);
        assertTrue(LocationStateOps.filterByDimension(state, "minecraft:the_end").isEmpty());
    }

    @Test
    void dropsNullEntriesWhileFiltering() {
        Map<UUID, PointSnapshot> state = new HashMap<>();
        PointSnapshot p = point("ow", "minecraft:overworld");
        state.put(p.id(), p);
        state.put(null, point("bad", "minecraft:overworld"));
        UUID danglingKey = UUID.nameUUIDFromBytes("dangling".getBytes());
        state.put(danglingKey, null);

        Map<UUID, PointSnapshot> filtered = LocationStateOps.filterByDimension(state, "minecraft:overworld");
        assertEquals(1, filtered.size());
        assertTrue(filtered.containsKey(p.id()));
    }
}
