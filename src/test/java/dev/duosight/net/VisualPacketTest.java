package dev.duosight.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualPacketTest {
    @Test
    void acceptsNormalPoseAndCursor() {
        assertTrue(new Packets.Pose(2, "world", 5, 1, 65.62, 2, -180, 30).valid());
        assertTrue(new Packets.Cursor(2, "world", 0, -150, 90).valid());
    }

    @Test
    void rejectsNonFiniteAndOutOfRangePose() {
        assertFalse(new Packets.Pose(2, "world", 5, Double.NaN, 65, 2, 0, 0).valid());
        assertFalse(new Packets.Pose(2, "world", 5, 1, 65, 2, Float.POSITIVE_INFINITY, 0).valid());
        assertFalse(new Packets.Pose(2, "world", 5, 1, 65, 2, 0, 91).valid());
        assertFalse(new Packets.Pose(2, "world", -1, 1, 65, 2, 0, 0).valid());
        assertFalse(new Packets.Pose(2, "world", 5, 30000001, 65, 2, 0, 0).valid());
    }

    @Test
    void rejectsInvalidCursor() {
        assertFalse(new Packets.Cursor(-1, "world", 0, 0, 0).valid());
        assertFalse(new Packets.Cursor(2, "world", -1, 0, 0).valid());
        assertFalse(new Packets.Cursor(2, "world", 0, Double.NaN, 0).valid());
        assertFalse(new Packets.Cursor(2, "world", 0, 0, 10001).valid());
    }
}
