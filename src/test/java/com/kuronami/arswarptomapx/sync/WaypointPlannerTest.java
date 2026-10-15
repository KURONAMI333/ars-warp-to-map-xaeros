package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「巻物の行き先 → あるべきウェイポイント集合」変換の単体テスト。MC クラスパス無しで完結。
 */
class WaypointPlannerTest {

    private static ScrollDestination dest(String name, int x, int y, int z, String dimension) {
        return new ScrollDestination(name, x, y, z, dimension);
    }

    @Test
    void emptyInputYieldsEmptyPlan() {
        assertTrue(WaypointPlanner.plan(List.of()).isEmpty());
        assertTrue(WaypointPlanner.plan(null).isEmpty());
    }

    @Test
    void eachDestinationBecomesOneWaypoint() {
        Map<UUID, PointSnapshot> plan = WaypointPlanner.plan(List.of(
                dest("Base", 10, 64, -20, "minecraft:overworld"),
                dest("Nether Hub", 5, 40, 5, "minecraft:the_nether")));

        assertEquals(2, plan.size());
        assertTrue(plan.values().stream().allMatch(p -> p.rgb() == WaypointPlanner.WAYPOINT_RGB));
    }

    @Test
    void twoScrollsToTheSameDestinationCollapseToOne() {
        Map<UUID, PointSnapshot> plan = WaypointPlanner.plan(List.of(
                dest("First", 100, 70, 100, "minecraft:overworld"),
                dest("Second", 100, 70, 100, "minecraft:overworld")));

        assertEquals(1, plan.size());
        // 集約時は最初に現れた巻物の名前を採用する（インベントリ順で決定的）
        assertEquals("First", plan.values().iterator().next().name());
    }

    @Test
    void sameCoordinatesInDifferentDimensionsStayDistinct() {
        Map<UUID, PointSnapshot> plan = WaypointPlanner.plan(List.of(
                dest("Overworld", 1, 2, 3, "minecraft:overworld"),
                dest("Nether", 1, 2, 3, "minecraft:the_nether")));

        assertEquals(2, plan.size());
    }

    @Test
    void renamedScrollKeepsItsProvidedName() {
        Map<UUID, PointSnapshot> plan = WaypointPlanner.plan(List.of(
                dest("Home Base", 0, 64, 0, "minecraft:overworld")));

        assertEquals("Home Base", plan.values().iterator().next().name());
    }

    @Test
    void destinationWithoutDimensionIsSkipped() {
        Map<UUID, PointSnapshot> plan = WaypointPlanner.plan(List.of(
                dest("Broken", 0, 0, 0, ""),
                dest("Good", 1, 1, 1, "minecraft:overworld")));

        assertEquals(1, plan.size());
        assertEquals("Good", plan.values().iterator().next().name());
    }

    @Test
    void droppingAScrollIsDetectedAsAStateChange() {
        Map<UUID, PointSnapshot> before = WaypointPlanner.plan(List.of(
                dest("Base", 1, 2, 3, "minecraft:overworld")));
        Map<UUID, PointSnapshot> after = WaypointPlanner.plan(List.of());

        assertTrue(LocationStateOps.stateChanged(before, after));
        assertTrue(LocationStateOps.filterByDimension(after, "minecraft:overworld").isEmpty());
    }
}
