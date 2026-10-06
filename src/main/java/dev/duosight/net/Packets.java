package dev.duosight.net;

import dev.duosight.DuoSight;
import dev.duosight.client.DuoClient;
import dev.duosight.core.GuideAction;
import dev.duosight.server.Sessions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class Packets {
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(DuoSight.ID, "play"))
            .networkProtocolVersion(5).simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(State.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::write).decoder(State::read)
                .consumerMainThread((packet, context) -> DuoClient.state(packet)).add();
        CHANNEL.messageBuilder(Input.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Input::write).decoder(Input::read)
                .consumerNetworkThread((packet, context) -> {
                    Sessions.input(context.getConnection(), packet);
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(ForwardedInput.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> packet.input.write(buffer))
                .decoder(buffer -> new ForwardedInput(Input.read(buffer)))
                .consumerMainThread((packet, context) -> DuoClient.remoteInput(packet.input)).add();
        CHANNEL.messageBuilder(View.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(View::write).decoder(View::read)
                .consumerMainThread((packet, context) -> Sessions.view(context.getSender(), packet)).add();
        CHANNEL.messageBuilder(ForwardedView.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> packet.view.write(buffer))
                .decoder(buffer -> new ForwardedView(View.read(buffer)))
                .consumerMainThread((packet, context) -> DuoClient.view(packet.view)).add();
        CHANNEL.messageBuilder(Stop.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> {})
                .decoder(buffer -> new Stop())
                .consumerMainThread((packet, context) -> Sessions.stop(context.getSender(), "duosight.stopped")).add();
        CHANNEL.messageBuilder(Menu.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> buffer.writeBoolean(packet.open))
                .decoder(buffer -> new Menu(buffer.readBoolean()))
                .consumerMainThread((packet, context) -> Sessions.menu(context.getSender(), packet.open)).add();
        CHANNEL.messageBuilder(Ready.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> buffer.writeVarInt(packet.epoch).writeUtf(packet.dimension, 256))
                .decoder(buffer -> new Ready(buffer.readVarInt(), buffer.readUtf(256)))
                .consumerMainThread((packet, context) -> Sessions.ready(context.getSender(), packet)).add();
        CHANNEL.messageBuilder(Pose.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Pose::write).decoder(Pose::read)
                .consumerNetworkThread((packet, context) -> {
                    Sessions.pose(context.getConnection(), packet);
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(ForwardedPose.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> packet.pose.write(buffer))
                .decoder(buffer -> new ForwardedPose(Pose.read(buffer)))
                .consumerMainThread((packet, context) -> DuoClient.pose(packet.pose)).add();
        CHANNEL.messageBuilder(Cursor.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Cursor::write).decoder(Cursor::read)
                .consumerNetworkThread((packet, context) -> {
                    Sessions.cursor(context.getConnection(), packet);
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(ForwardedCursor.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> packet.cursor.write(buffer))
                .decoder(buffer -> new ForwardedCursor(Cursor.read(buffer)))
                .consumerMainThread((packet, context) -> DuoClient.cursor(packet.cursor)).add();
        CHANNEL.messageBuilder(Book.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> {
                    buffer.writeBoolean(packet.refresh);
                    buffer.writeCollection(packet.pages, (output, page) -> output.writeUtf(page, 16384));
                })
                .decoder(buffer -> new Book(buffer.readBoolean(),
                        buffer.readList(input -> input.readUtf(16384))))
                .consumerMainThread((packet, context) -> DuoClient.book(packet)).add();
        CHANNEL.messageBuilder(OpenBook.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> buffer.writeBoolean(packet.refresh))
                .decoder(buffer -> new OpenBook(buffer.readBoolean()))
                .consumerMainThread((packet, context) -> Sessions.book(context.getSender(), packet.refresh)).add();
        CHANNEL.messageBuilder(BookAction.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BookAction::write).decoder(BookAction::read)
                .consumerMainThread((packet, context) -> Sessions.bookAction(context.getSender(), packet.action)).add();
        CHANNEL.build();
    }

    public static void send(ServerPlayer player, Object packet) {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    public static void server(Object packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    public static void send(Connection connection, Object packet) {
        CHANNEL.send(packet, new PacketDistributor.PacketTarget(connection::send, NetworkDirection.PLAY_TO_CLIENT));
    }

    public record State(boolean active, boolean body, boolean driver, int bodyId,
                        int seconds, int epoch, boolean changed, String dimension,
                        boolean travelling, boolean suspended) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(active).writeBoolean(body).writeBoolean(driver);
            buffer.writeVarInt(bodyId).writeVarInt(seconds).writeVarInt(epoch).writeBoolean(changed);
            buffer.writeUtf(dimension, 256).writeBoolean(travelling).writeBoolean(suspended);
        }

        static State read(FriendlyByteBuf buffer) {
            return new State(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readUtf(256), buffer.readBoolean(), buffer.readBoolean());
        }
    }

    public record Input(int epoch, int kind, int code, int scan, int action, int modifiers,
                        double x, double y) {
        public static final int KEY = 0, CHARACTER = 1, BUTTON = 2, LOOK = 3, CURSOR = 4, SCROLL = 5;

        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(epoch).writeByte(kind).writeInt(code).writeInt(scan);
            buffer.writeByte(action).writeInt(modifiers).writeDouble(x).writeDouble(y);
        }

        static Input read(FriendlyByteBuf buffer) {
            return new Input(buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readInt(),
                    buffer.readInt(), buffer.readUnsignedByte(), buffer.readInt(),
                    buffer.readDouble(), buffer.readDouble());
        }

        public boolean valid() {
            return epoch >= 0 && kind >= KEY && kind <= SCROLL && Double.isFinite(x)
                    && Double.isFinite(y) && Math.abs(x) <= 10000 && Math.abs(y) <= 10000
                    && action >= 0 && action <= 2 && modifiers >= 0 && modifiers <= 63
                    && (kind != KEY || (code >= -1 && code <= 348))
                    && (kind != BUTTON || (code >= 0 && code <= 7))
                    && (kind != CHARACTER || Character.isValidCodePoint(code));
        }
    }

    public record ForwardedInput(Input input) {}

    public record View(int epoch, CompoundTag data) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(epoch).writeNbt(data);
        }

        static View read(FriendlyByteBuf buffer) {
            int epoch = buffer.readVarInt();
            CompoundTag data = buffer.readNbt();
            return new View(epoch, data == null ? new CompoundTag() : data);
        }
    }

    public record ForwardedView(View view) {}
    public record Stop() {}
    public record Menu(boolean open) {}
    public record Ready(int epoch, String dimension) {}

    public record Pose(int epoch, String dimension, int sequence, double x, double y, double z,
                       float yaw, float pitch) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(epoch).writeUtf(dimension, 256).writeVarInt(sequence);
            buffer.writeDouble(x).writeDouble(y).writeDouble(z).writeFloat(yaw).writeFloat(pitch);
        }

        static Pose read(FriendlyByteBuf buffer) {
            return new Pose(buffer.readVarInt(), buffer.readUtf(256), buffer.readVarInt(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readFloat());
        }

        public boolean valid() {
            return epoch >= 0 && sequence >= 0 && Double.isFinite(x) && Double.isFinite(y)
                    && Double.isFinite(z) && Math.abs(x) <= 30000000 && Math.abs(y) <= 30000000
                    && Math.abs(z) <= 30000000 && Float.isFinite(yaw)
                    && Float.isFinite(pitch) && Math.abs(pitch) <= 90;
        }
    }

    public record ForwardedPose(Pose pose) {}

    public record Cursor(int epoch, String dimension, int menuId, double x, double y) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(epoch).writeUtf(dimension, 256).writeVarInt(menuId);
            buffer.writeDouble(x).writeDouble(y);
        }

        static Cursor read(FriendlyByteBuf buffer) {
            return new Cursor(buffer.readVarInt(), buffer.readUtf(256), buffer.readVarInt(),
                    buffer.readDouble(), buffer.readDouble());
        }

        public boolean valid() {
            return epoch >= 0 && menuId >= 0 && Double.isFinite(x) && Double.isFinite(y)
                    && Math.abs(x) <= 10000 && Math.abs(y) <= 10000;
        }
    }

    public record ForwardedCursor(Cursor cursor) {}
    public record Book(boolean refresh, java.util.List<String> pages) {}
    public record OpenBook(boolean refresh) {}
    public record BookAction(GuideAction action) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeEnum(action.kind()).writeVarInt(action.value()).writeUtf(action.player(), 16);
        }

        static BookAction read(FriendlyByteBuf buffer) {
            return new BookAction(new GuideAction(buffer.readEnum(GuideAction.Kind.class),
                    buffer.readVarInt(), buffer.readUtf(16)));
        }
    }

    private Packets() {}
}
