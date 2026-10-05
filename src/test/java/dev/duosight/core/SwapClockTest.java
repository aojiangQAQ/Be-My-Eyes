package dev.duosight.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwapClockTest {
    @Test
    void beginsWithBodyInControl() {
        var clock = new SwapClock(120);
        assertTrue(clock.bodyControls());
        assertEquals(120, clock.seconds());
        assertEquals(0, clock.epoch());
    }

    @Test
    void swapsExactlyAtTheDeadline() {
        var clock = new SwapClock(120);
        for (int i = 0; i < 2399; i++) {
            assertFalse(clock.tick());
        }
        assertEquals(1, clock.seconds());
        assertTrue(clock.tick());
        assertFalse(clock.bodyControls());
        assertEquals(120, clock.seconds());
        assertEquals(1, clock.epoch());
    }

    @Test
    void twoSwapsRestoreTheOriginalRoles() {
        var clock = new SwapClock(1);
        for (int i = 0; i < 40; i++) {
            clock.tick();
        }
        assertTrue(clock.bodyControls());
        assertEquals(2, clock.epoch());
    }

    @Test
    void roundsRemainingSecondsUp() {
        var clock = new SwapClock(2);
        clock.tick();
        assertEquals(2, clock.seconds());
        for (int i = 0; i < 19; i++) {
            clock.tick();
        }
        assertEquals(1, clock.seconds());
    }

    @Test
    void rejectsInvalidIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new SwapClock(0));
        assertThrows(IllegalArgumentException.class, () -> new SwapClock(3601));
    }

    @Test
    void invalidationPreservesRoleAndRemainingTime() {
        var clock = new SwapClock(120);
        for (int i = 0; i < 35; i++) {
            clock.tick();
        }
        int remaining = clock.seconds();
        clock.invalidate();
        assertEquals(1, clock.epoch());
        assertTrue(clock.bodyControls());
        assertEquals(remaining, clock.seconds());
    }

    @Test
    void changingIntervalResetsCountdownWithoutChangingRolesOrEpoch() {
        var clock = new SwapClock(120);
        clock.swap();
        for (int i = 0; i < 35; i++) {
            clock.tick();
        }
        clock.setInterval(30);
        assertEquals(30, clock.intervalSeconds());
        assertEquals(30, clock.seconds());
        assertFalse(clock.bodyControls());
        assertEquals(1, clock.epoch());
        for (int i = 0; i < 599; i++) {
            assertFalse(clock.tick());
        }
        assertTrue(clock.tick());
        assertTrue(clock.bodyControls());
        assertEquals(30, clock.seconds());
        assertEquals(2, clock.epoch());
    }

    @Test
    void manualSwapResetsCountdownToTheUpdatedInterval() {
        var clock = new SwapClock(120);
        clock.setInterval(15);
        clock.tick();
        clock.swap();
        assertFalse(clock.bodyControls());
        assertEquals(15, clock.seconds());
        assertEquals(1, clock.epoch());
        clock.swap();
        assertTrue(clock.bodyControls());
        assertEquals(15, clock.seconds());
        assertEquals(2, clock.epoch());
    }

    @Test
    void invalidIntervalUpdateLeavesClockUnchanged() {
        var clock = new SwapClock(120);
        clock.tick();
        assertThrows(IllegalArgumentException.class, () -> clock.setInterval(0));
        assertThrows(IllegalArgumentException.class, () -> clock.setInterval(3601));
        assertEquals(120, clock.intervalSeconds());
        assertEquals(120, clock.seconds());
        assertTrue(clock.bodyControls());
        assertEquals(0, clock.epoch());
        for (int i = 0; i < 19; i++) {
            clock.tick();
        }
        assertEquals(119, clock.seconds());
    }
}
