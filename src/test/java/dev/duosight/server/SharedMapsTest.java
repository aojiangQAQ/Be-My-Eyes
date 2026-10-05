package dev.duosight.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedMapsTest {
    @Test
    void initialPatchIncludesAllPixelsAndOwnsItsArray() {
        byte[] colors = new byte[16384];
        colors[64] = 7;
        var patch = SharedMaps.patch(null, colors);
        assertEquals(128, patch.width());
        assertEquals(128, patch.height());
        assertArrayEquals(colors, patch.mapColors());
        colors[64] = 9;
        assertEquals(7, patch.mapColors()[64]);
    }

    @Test
    void unchangedPixelsDoNotSendAPatch() {
        assertNull(SharedMaps.patch(new byte[16384], new byte[16384]));
    }

    @Test
    void changedRectanglePreservesRowOrder() {
        byte[] before = new byte[16384];
        byte[] after = before.clone();
        after[3 + 2 * 128] = 4;
        after[5 + 4 * 128] = 8;
        var patch = SharedMaps.patch(before, after);
        assertEquals(3, patch.startX());
        assertEquals(2, patch.startY());
        assertEquals(3, patch.width());
        assertEquals(3, patch.height());
        assertArrayEquals(new byte[]{4, 0, 0, 0, 0, 0, 0, 0, 8}, patch.mapColors());
    }

    @Test
    void oppositeCornersIncludeTheWholeMap() {
        byte[] before = new byte[16384];
        byte[] after = before.clone();
        after[0] = 1;
        after[16383] = 2;
        var patch = SharedMaps.patch(before, after);
        assertEquals(0, patch.startX());
        assertEquals(0, patch.startY());
        assertArrayEquals(after, patch.mapColors());
    }
}
