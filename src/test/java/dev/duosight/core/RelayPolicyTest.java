package dev.duosight.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RelayPolicyTest {
    @Test
    void onlyTheCurrentGuestDriverCanRelayInput() {
        assertTrue(new RelayPolicy(2, "world", false, true, false, false).input(2));
        assertFalse(new RelayPolicy(2, "world", true, true, false, false).input(2));
        assertFalse(new RelayPolicy(2, "world", false, false, false, false).input(2));
        assertFalse(new RelayPolicy(2, "world", false, true, true, false).input(2));
        assertFalse(new RelayPolicy(2, "world", false, true, false, false).input(1));
    }

    @Test
    void visualsMustComeFromTheBodyInTheCurrentDimensionAndEpoch() {
        var policy = new RelayPolicy(3, "nether", true, true, false, false);
        assertTrue(policy.visual(3, "nether"));
        assertFalse(policy.visual(2, "nether"));
        assertFalse(policy.visual(3, "end"));
        assertFalse(new RelayPolicy(3, "nether", false, true, false, false).visual(3, "nether"));
        assertFalse(new RelayPolicy(3, "nether", true, true, true, true).visual(3, "nether"));
    }

    @Test
    void menuSuspensionDoesNotPreventSeeingTheBody() {
        var policy = new RelayPolicy(3, "world", true, true, true, false);
        assertTrue(policy.visual(3, "world"));
        assertFalse(policy.input(3));
    }
}
