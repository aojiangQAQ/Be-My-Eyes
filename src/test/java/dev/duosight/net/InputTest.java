package dev.duosight.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputTest {
    private Packets.Input input(int kind, int code, double x, double y) {
        return new Packets.Input(0, kind, code, 0, 1, 0, x, y);
    }

    @Test
    void permitsNormalInputKinds() {
        assertTrue(input(Packets.Input.KEY, 87, 0, 0).valid());
        assertTrue(input(Packets.Input.BUTTON, 0, 0, 0).valid());
        assertTrue(input(Packets.Input.LOOK, 0, -200, 100).valid());
        assertTrue(input(Packets.Input.CURSOR, 0, 0.5, 0.5).valid());
        assertTrue(input(Packets.Input.SCROLL, 0, 0, -1).valid());
        assertTrue(input(Packets.Input.CHARACTER, 'a', 0, 0).valid());
    }

    @Test
    void rejectsNonFiniteMouseMovement() {
        assertFalse(input(Packets.Input.LOOK, 0, Double.NaN, 0).valid());
        assertFalse(input(Packets.Input.LOOK, 0, 0, Double.POSITIVE_INFINITY).valid());
    }

    @Test
    void rejectsOutOfBoundsInput() {
        assertFalse(input(Packets.Input.BUTTON, 8, 0, 0).valid());
        assertFalse(input(Packets.Input.KEY, 349, 0, 0).valid());
        assertFalse(input(6, 0, 0, 0).valid());
        assertTrue(input(Packets.Input.CURSOR, 0, -150, 90).valid());
        assertFalse(input(Packets.Input.CURSOR, 0, 0, 10001).valid());
        assertFalse(input(Packets.Input.LOOK, 0, 10001, 0).valid());
    }
}
