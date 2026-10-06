package dev.duosight.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuideActionTest {
    @Test
    void onlyAcceptsKnownModActions() {
        for (String command : List.of("bemyeyes swap", "/op Friend", "/duosight swap",
                "/bemyeyes unknown", "/bemyeyes stop extra", "/bemyeyes book give extra",
                "/bemyeyes role unknown", "/bemyeyes interval 14", "/bemyeyes interval 3601",
                "/bemyeyes interval add 0", "/bemyeyes interval add -3601",
                "/bemyeyes invite @a", "/bemyeyes invite FarTooLongPlayerName")) {
            assertNull(GuideAction.fromClick(command), command);
        }
    }

    @Test
    void roundTripsCanonicalActions() {
        for (GuideAction action : List.of(
                new GuideAction(GuideAction.Kind.ROLE, 0, ""),
                new GuideAction(GuideAction.Kind.ROLE, 1, ""),
                new GuideAction(GuideAction.Kind.INTERVAL, 15, ""),
                new GuideAction(GuideAction.Kind.INTERVAL, 3600, ""),
                new GuideAction(GuideAction.Kind.ADJUST, -60, ""),
                new GuideAction(GuideAction.Kind.ADJUST, 1, ""),
                new GuideAction(GuideAction.Kind.INVITE, 0, "Friend_1"),
                new GuideAction(GuideAction.Kind.ACCEPT, 0, ""),
                new GuideAction(GuideAction.Kind.DECLINE, 0, ""),
                new GuideAction(GuideAction.Kind.SWAP, 0, ""),
                new GuideAction(GuideAction.Kind.STOP, 0, ""),
                new GuideAction(GuideAction.Kind.GIVE, 0, ""),
                new GuideAction(GuideAction.Kind.REFRESH, 0, ""))) {
            assertTrue(action.valid());
            assertEquals(action, GuideAction.fromClick("/" + action.command()));
        }
    }

    @Test
    void validatesPacketArgumentsWithoutAllowingCommandInjection() {
        assertFalse(new GuideAction(GuideAction.Kind.INVITE, 0, "Friend\nop Friend").valid());
        assertFalse(new GuideAction(GuideAction.Kind.INVITE, 1, "Friend").valid());
        assertFalse(new GuideAction(GuideAction.Kind.STOP, 0, "Friend").valid());
        assertFalse(new GuideAction(GuideAction.Kind.ROLE, 2, "").valid());
        assertFalse(new GuideAction(GuideAction.Kind.ADJUST, Integer.MAX_VALUE, "").valid());
        assertFalse(new GuideAction(null, 0, "").valid());
        assertFalse(new GuideAction(GuideAction.Kind.REFRESH, 0, null).valid());
    }

    @Test
    void closesOnlyActionsThatLeaveTheCurrentSettings() {
        assertTrue(new GuideAction(GuideAction.Kind.ACCEPT, 0, "").closes());
        assertTrue(new GuideAction(GuideAction.Kind.STOP, 0, "").closes());
        assertFalse(new GuideAction(GuideAction.Kind.ADJUST, 1, "").closes());
        assertFalse(new GuideAction(GuideAction.Kind.REFRESH, 0, "").closes());
    }

    @Test
    void clampsCustomIntervalsAndPreservesTheOpeningRole() {
        assertEquals(new PairingOptions(false, 121), new PairingOptions(false, 120).adjust(1));
        assertEquals(15, new PairingOptions(true, 16).adjust(-60).seconds());
        assertEquals(3600, new PairingOptions(true, 3599).adjust(60).seconds());
        assertEquals(3600, new PairingOptions(true, 120).adjust(Integer.MAX_VALUE).seconds());
        assertEquals(15, new PairingOptions(true, 120).adjust(Integer.MIN_VALUE).seconds());
    }
}
