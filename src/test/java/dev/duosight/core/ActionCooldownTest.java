package dev.duosight.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionCooldownTest {
    @Test
    void rejectsInvalidIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new ActionCooldown(0));
        assertThrows(IllegalArgumentException.class, () -> new ActionCooldown(-1));
    }

    @Test
    void permitsTheFirstActionAndTheExactBoundary() {
        ActionCooldown cooldown = new ActionCooldown(5);
        assertTrue(cooldown.allow(100));
        assertFalse(cooldown.allow(100));
        assertFalse(cooldown.allow(104));
        assertTrue(cooldown.allow(105));
    }

    @Test
    void rejectedBurstsDoNotExtendTheCooldown() {
        ActionCooldown cooldown = new ActionCooldown(300);
        assertTrue(cooldown.allow(0));
        for (int time = 0; time < 300; time++) {
            assertFalse(cooldown.allow(time));
        }
        assertTrue(cooldown.allow(300));
    }

    @Test
    void independentPlayersAndDisconnectResetRemainIndependent() {
        ActionCooldown first = new ActionCooldown(5);
        ActionCooldown second = new ActionCooldown(5);
        assertTrue(first.allow(100));
        assertTrue(second.allow(100));
        assertFalse(first.allow(101));
        first.reset();
        assertTrue(first.allow(0));
        assertFalse(second.allow(101));
    }
}
