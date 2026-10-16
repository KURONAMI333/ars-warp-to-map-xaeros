package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自作ウェイポイント記録（WaypointRecord）の永続化フォーマットのテスト。
 * 壊れた 1 行で全体を失わないこと、区切り・非 ASCII の名前を往復できることを守る。
 */
class WaypointRecordTest {

    private static UUID id(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes());
    }

    private static PointSnapshot point(String seed, String name, int x, int y, int z,
                                       String dimension, int rgb) {
        return new PointSnapshot(id(seed), name, x, y, z, dimension, rgb);
    }

    @Test
    void roundTripPreservesEntries() {
        WaypointRecord record = WaypointRecord.empty();
        PointSnapshot a = point("a", "Base", 10, 64, -20, "minecraft:overworld", 0xFF6A00);
        PointSnapshot b = point("b", "ネザー拠点", 3, 70, 4, "minecraft:the_nether", 0x00AAFF);
        record.put(a);
        record.put(b);

        WaypointRecord parsed = WaypointRecord.parse(record.serialize());

        assertEquals(2, parsed.size());
        assertEquals(a, parsed.get(a.id()));
        assertEquals(b, parsed.get(b.id()));
        assertEquals(record.serialize(), parsed.serialize());
    }

    @Test
    void namesWithSeparatorAndNewlineSurvive() {
        WaypointRecord record = WaypointRecord.empty();
        PointSnapshot tricky = point("t", "a|b\nc 空白", 1, 2, 3, "minecraft:overworld", 0x123456);
        record.put(tricky);

        WaypointRecord parsed = WaypointRecord.parse(record.serialize());

        assertEquals("a|b\nc 空白", parsed.get(tricky.id()).name());
        assertEquals(1, parsed.size());
    }

    @Test
    void malformedLinesAreSkippedNotFatal() {
        String text = ""
                + "not-a-uuid|1|2|3|4|minecraft:overworld|Base\n"
                + "missing|fields\n"
                + "\n"
                + "   \n"
                + id("ok") + "|5|6|7|8|minecraft:overworld|Good\n";

        WaypointRecord parsed = WaypointRecord.parse(text);

        assertEquals(1, parsed.size());
        assertEquals("Good", parsed.get(id("ok")).name());
    }

    @Test
    void malformedNonBlankLineMarksRecordUnhealthy() {
        String text = ""
                + id("ok") + "|5|6|7|8|minecraft:overworld|Good\n"
                + "not-a-uuid|1|2|3|4|minecraft:overworld|Base\n";

        WaypointRecord parsed = WaypointRecord.parse(text);

        assertEquals(1, parsed.size());
        assertTrue(parsed.hadMalformedLines());
    }

    @Test
    void wrongFieldCountMarksRecordUnhealthy() {
        WaypointRecord parsed = WaypointRecord.parse("missing|fields\n");

        assertTrue(parsed.isEmpty());
        assertTrue(parsed.hadMalformedLines());
    }

    @Test
    void blankLinesAndCleanParseAreHealthy() {
        WaypointRecord clean = WaypointRecord.parse(id("a") + "|1|2|3|10|minecraft:overworld|A\n");
        assertFalse(clean.hadMalformedLines());

        WaypointRecord blanks = WaypointRecord.parse("\n   \n" + id("a") + "|1|2|3|10|minecraft:overworld|A\n");
        assertFalse(blanks.hadMalformedLines());

        assertFalse(WaypointRecord.parse("").hadMalformedLines());
        assertFalse(WaypointRecord.parse(null).hadMalformedLines());
    }

    @Test
    void replaceDimensionSwapsOnlyThatDimension() {
        WaypointRecord record = WaypointRecord.empty();
        PointSnapshot overOld = point("o", "Old", 0, 0, 0, "minecraft:overworld", 0);
        PointSnapshot nether = point("n", "N", 1, 1, 1, "minecraft:the_nether", 0);
        record.put(overOld);
        record.put(nether);

        PointSnapshot overNew = point("o2", "New", 5, 5, 5, "minecraft:overworld", 0);
        record.replaceDimension("minecraft:overworld", Map.of(overNew.id(), overNew));

        assertEquals(Map.of(overNew.id(), overNew), record.forDimension("minecraft:overworld"));
        assertEquals(Map.of(nether.id(), nether), record.forDimension("minecraft:the_nether"));
    }

    @Test
    void replaceDimensionWithNullContentClearsThatDimension() {
        WaypointRecord record = WaypointRecord.empty();
        record.put(point("a", "A", 0, 0, 0, "minecraft:overworld", 0));

        record.replaceDimension("minecraft:overworld", null);

        assertTrue(record.isEmpty());
    }

    @Test
    void forDimensionFiltersByDimensionId() {
        WaypointRecord record = WaypointRecord.empty();
        PointSnapshot overworld = point("o", "O", 0, 0, 0, "minecraft:overworld", 0);
        PointSnapshot nether = point("n", "N", 1, 1, 1, "minecraft:the_nether", 0);
        record.put(overworld);
        record.put(nether);

        Map<UUID, PointSnapshot> onlyOverworld = record.forDimension("minecraft:overworld");

        assertEquals(1, onlyOverworld.size());
        assertTrue(onlyOverworld.containsKey(overworld.id()));
        assertTrue(record.forDimension(null).isEmpty());
        assertTrue(record.forDimension("minecraft:the_end").isEmpty());
    }

    @Test
    void removeAndClearWork() {
        WaypointRecord record = WaypointRecord.empty();
        PointSnapshot a = point("a", "A", 0, 0, 0, "minecraft:overworld", 0);
        record.put(a);

        assertEquals(a, record.remove(a.id()));
        assertTrue(record.isEmpty());
        assertNull(record.remove(a.id()));

        record.put(a);
        record.clear();
        assertTrue(record.isEmpty());
    }

    @Test
    void emptyRecordSerializesEmptyAndParsesToEmpty() {
        assertTrue(WaypointRecord.empty().serialize().isEmpty());
        assertTrue(WaypointRecord.parse(null).isEmpty());
        assertTrue(WaypointRecord.parse("").isEmpty());
    }

    @Test
    void copyIsIndependent() {
        WaypointRecord original = WaypointRecord.empty();
        original.put(point("a", "A", 0, 0, 0, "minecraft:overworld", 0));

        WaypointRecord copy = new WaypointRecord(original);
        copy.remove(id("a"));

        assertFalse(original.isEmpty());
        assertTrue(copy.isEmpty());
    }
}
