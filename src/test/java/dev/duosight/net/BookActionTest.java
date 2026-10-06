package dev.duosight.net;

import dev.duosight.core.GuideAction;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookActionTest {
    @Test
    void roundTripsAnInvitePacket() {
        roundTrip(new GuideAction(GuideAction.Kind.INVITE, 0, "Friend_1"));
    }

    @Test
    void roundTripsSignedIntervalAdjustments() {
        roundTrip(new GuideAction(GuideAction.Kind.ADJUST, -60, ""));
        roundTrip(new GuideAction(GuideAction.Kind.ADJUST, 1, ""));
    }

    private static void roundTrip(GuideAction action) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new Packets.BookAction(action).write(buffer);
            assertEquals(action, Packets.BookAction.read(buffer).action());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
