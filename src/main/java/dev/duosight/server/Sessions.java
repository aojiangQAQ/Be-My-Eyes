package dev.duosight.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.duosight.DuoConfig;
import dev.duosight.core.ActionCooldown;
import dev.duosight.core.GuideAction;
import dev.duosight.core.GuidePages;
import dev.duosight.core.PairingOptions;
import dev.duosight.core.SwapClock;
import dev.duosight.core.TravelGate;
import dev.duosight.core.RelayPolicy;
import dev.duosight.net.Packets;
import dev.duosight.mixin.MenuAccess;
import dev.duosight.mixin.PlayerListAccess;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Sessions {
    private static final String RESTORE = "duosight_restore";
    private static final Map<UUID, Session> MEMBERS = new HashMap<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<Connection, Route> ROUTES = new ConcurrentHashMap<>();
    private static final Map<UUID, ActionCooldown> BOOK_REQUESTS = new HashMap<>();
    private static final Map<UUID, ActionCooldown> BOOK_ACTIONS = new HashMap<>();
    private static final Set<UUID> BOOK_REFRESHES = new HashSet<>();

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        var root = event.getDispatcher().register(Commands.literal("bemyeyes")
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            var body = context.getSource().getPlayerOrException();
                            var guest = EntityArgument.getPlayer(context, "player");
                            if (body == guest || MEMBERS.containsKey(body.getUUID())
                                    || MEMBERS.containsKey(guest.getUUID())) {
                                body.sendSystemMessage(Component.translatable("duosight.unavailable"));
                                return 0;
                            }
                            long deadline = body.getServer().getTickCount() + DuoConfig.INVITE_SECONDS.get() * 20L;
                            PairingOptions options = GuideBook.options(body);
                            INVITES.put(guest.getUUID(), new Invite(body.getUUID(), deadline, options));
                            body.sendSystemMessage(Component.translatable("duosight.invited", guest.getName()));
                            guest.sendSystemMessage(Component.translatable("duosight.invitation", body.getName(),
                                    Component.translatable(options.driver() ? "duosight.observer" : "duosight.driver"),
                                    options.seconds()).append(" ").append(
                                    Component.translatable("duosight.book.accept").withStyle(style ->
                                            style.withColor(ChatFormatting.GREEN).withUnderlined(true)
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                            "/bemyeyes accept")))));
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("accept").executes(context -> {
                    var guest = context.getSource().getPlayerOrException();
                    Invite invite = INVITES.remove(guest.getUUID());
                    var server = guest.getServer();
                    ServerPlayer body = invite == null ? null : server.getPlayerList().getPlayer(invite.body);
                    if (body == null || invite.deadline < server.getTickCount()) {
                        guest.sendSystemMessage(Component.translatable("duosight.no_invite"));
                        return 0;
                    }
                    return start(body, guest, invite.options) ? Command.SINGLE_SUCCESS : 0;
                }))
                .then(Commands.literal("decline").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (INVITES.remove(player.getUUID()) == null) {
                        player.sendSystemMessage(Component.translatable("duosight.no_invite"));
                        return 0;
                    }
                    player.sendSystemMessage(Component.translatable("duosight.book.declined"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("book").executes(context ->
                        book(context.getSource().getPlayerOrException()))
                        .then(Commands.literal("give").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            if (MEMBERS.containsKey(player.getUUID())) {
                                player.sendSystemMessage(Component.translatable("duosight.book.give_active"));
                                return 0;
                            }
                            return GuideBook.give(player);
                        })))
                .then(Commands.literal("role")
                        .then(Commands.literal("driver").executes(context ->
                                role(context.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("eyes").executes(context ->
                                role(context.getSource().getPlayerOrException(), false))))
                .then(Commands.literal("stop").executes(context -> {
                    stop(context.getSource().getPlayerOrException(), "duosight.stopped");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("interval")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(15, 3600))
                                .executes(context -> interval(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "seconds"))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(-3600, 3600))
                                        .executes(context -> adjust(context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "seconds"), true)))))
                .then(Commands.literal("swap").executes(context ->
                        swap(context.getSource().getPlayerOrException())))
                .then(Commands.literal("status").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    Session session = MEMBERS.get(player.getUUID());
                    player.sendSystemMessage(session == null
                            ? Component.translatable("duosight.inactive")
                            : Component.translatable("duosight.status",
                                    session.clock.intervalSeconds(), session.clock.seconds()));
                    return Command.SINGLE_SUCCESS;
                })));
        event.getDispatcher().register(Commands.literal("duosight").redirect(root));
    }

    public static int book(ServerPlayer player) {
        return book(player, false);
    }

    public static int book(ServerPlayer player, boolean refresh) {
        if (player == null || player.hasDisconnected()) {
            return 0;
        }
        if (!BOOK_REQUESTS.computeIfAbsent(player.getUUID(), id -> new ActionCooldown(5))
                .allow(player.getServer().getTickCount())) {
            if (refresh) {
                BOOK_REFRESHES.add(player.getUUID());
            }
            return 0;
        }
        BOOK_REFRESHES.remove(player.getUUID());
        return sendBook(player, refresh);
    }

    public static void bookAction(ServerPlayer player, GuideAction action) {
        if (player == null || player.hasDisconnected() || action == null || !action.valid()) {
            return;
        }
        if (!BOOK_ACTIONS.computeIfAbsent(player.getUUID(), id -> new ActionCooldown(5))
                .allow(player.getServer().getTickCount())) {
            BOOK_REFRESHES.add(player.getUUID());
            return;
        }
        switch (action.kind()) {
            case INTERVAL -> interval(player, action.value(), false);
            case ADJUST -> adjust(player, action.value(), false);
            case REFRESH -> {}
            default -> player.getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(), action.command());
        }
        BOOK_REFRESHES.remove(player.getUUID());
        BOOK_REQUESTS.computeIfAbsent(player.getUUID(), id -> new ActionCooldown(5))
                .allow(player.getServer().getTickCount());
        if (!action.closes()) {
            sendBook(player, true);
        }
    }

    private static int sendBook(ServerPlayer player, boolean refresh) {
        Session session = MEMBERS.get(player.getUUID());
        if (session != null && (session.travel.waiting() || session.endPending)) {
            if (!refresh) {
                player.sendSystemMessage(Component.translatable("duosight.swap_loading"));
            }
            return 0;
        }
        GuidePages.Shared shared = session == null ? null : new GuidePages.Shared(
                (player == session.body ? session.guest : session.body).getGameProfile().getName(),
                (player == session.body) == session.clock.bodyControls(),
                session.clock.intervalSeconds(), session.clock.seconds());
        Invite invite = INVITES.get(player.getUUID());
        ServerPlayer inviter = invite == null || invite.deadline < player.getServer().getTickCount()
                ? null : player.getServer().getPlayerList().getPlayer(invite.body);
        GuidePages.Invitation invitation = inviter == null ? null : new GuidePages.Invitation(
                inviter.getGameProfile().getName(), invite.options.driver(), invite.options.seconds());
        var players = player.getServer().getPlayerList().getPlayers().stream()
                .filter(other -> other != player && !MEMBERS.containsKey(other.getUUID())
                        && other.serverLevel() == player.serverLevel() && other.distanceToSqr(player) <= 32 * 32
                        && other.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                        && !other.isPassenger() && !other.isDeadOrDying())
                .map(other -> other.getGameProfile().getName()).sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(128).toList();
        var pages = GuidePages.create(GuideBook.options(player), shared, invitation, players,
                !GuideBook.played(player));
        Packets.send(player, new Packets.Book(refresh, pages.stream()
                .map(page -> Component.Serializer.toJson(page, player.registryAccess())).toList()));
        return Command.SINGLE_SUCCESS;
    }

    private static int role(ServerPlayer player, boolean driver) {
        if (MEMBERS.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("duosight.book.role_active"));
            return 0;
        }
        GuideBook.options(player, new PairingOptions(driver, GuideBook.options(player).seconds()));
        player.sendSystemMessage(Component.translatable("duosight.book.role_set",
                Component.translatable(driver ? "duosight.driver" : "duosight.observer")));
        return Command.SINGLE_SUCCESS;
    }

    private static int interval(ServerPlayer player, int seconds) {
        return interval(player, seconds, true);
    }

    private static int interval(ServerPlayer player, int seconds, boolean announce) {
        Session session = MEMBERS.get(player.getUUID());
        if (session == null) {
            GuideBook.options(player, new PairingOptions(GuideBook.options(player).driver(), seconds));
            if (announce) {
                player.sendSystemMessage(Component.translatable("duosight.book.interval_set", seconds));
            }
            return Command.SINGLE_SUCCESS;
        }
        session.clock.setInterval(seconds);
        sync(session, false);
        if (announce) {
            Component message = Component.translatable("duosight.interval_set", seconds);
            session.body.sendSystemMessage(message);
            session.guest.sendSystemMessage(message);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int adjust(ServerPlayer player, int delta, boolean announce) {
        Session session = MEMBERS.get(player.getUUID());
        int current = session == null ? GuideBook.options(player).seconds() : session.clock.intervalSeconds();
        int seconds = new PairingOptions(true, current).adjust(delta).seconds();
        return current == seconds ? Command.SINGLE_SUCCESS : interval(player, seconds, announce);
    }

    private static int swap(ServerPlayer player) {
        Session session = MEMBERS.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.translatable("duosight.inactive"));
            return 0;
        }
        if (session.travel.waiting() || session.endPending) {
            player.sendSystemMessage(Component.translatable("duosight.swap_loading"));
            return 0;
        }
        session.clock.swap();
        sync(session, true);
        publishView(session, true);
        Component message = Component.translatable("duosight.swapped");
        session.body.sendSystemMessage(message);
        session.guest.sendSystemMessage(message);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean start(ServerPlayer body, ServerPlayer guest, PairingOptions options) {
        if (MEMBERS.containsKey(body.getUUID()) || MEMBERS.containsKey(guest.getUUID())
                || body.serverLevel() != guest.serverLevel() || body.distanceToSqr(guest) > 32 * 32
                || body.gameMode.getGameModeForPlayer() != GameType.SURVIVAL
                || guest.gameMode.getGameModeForPlayer() != GameType.SURVIVAL
                || body.isPassenger() || guest.isPassenger() || body.isDeadOrDying() || guest.isDeadOrDying()) {
            guest.sendSystemMessage(Component.translatable("duosight.start_conditions"));
            return false;
        }
        GuideBook.consume(body);
        GuideBook.consume(guest);
        body.closeContainer();
        guest.closeContainer();
        GuideBook.remove(body);
        GuideBook.remove(guest);
        ((PlayerListAccess) body.getServer().getPlayerList()).duosight$save(body);
        CompoundTag restore = new CompoundTag();
        restore.putString("dimension", guest.serverLevel().dimension().location().toString());
        restore.putDouble("x", guest.getX());
        restore.putDouble("y", guest.getY());
        restore.putDouble("z", guest.getZ());
        restore.putFloat("yaw", guest.getYRot());
        restore.putFloat("pitch", guest.getXRot());
        restore.putInt("mode", guest.gameMode.getGameModeForPlayer().getId());
        guest.getPersistentData().put(RESTORE, restore);
        ((PlayerListAccess) guest.getServer().getPlayerList()).duosight$save(guest);
        Session session = new Session(body, guest, options);
        MEMBERS.put(body.getUUID(), session);
        MEMBERS.put(guest.getUUID(), session);
        INVITES.entrySet().removeIf(entry -> entry.getKey().equals(body.getUUID())
                || entry.getKey().equals(guest.getUUID()) || entry.getValue().body.equals(body.getUUID())
                || entry.getValue().body.equals(guest.getUUID()));
        guest.setGameMode(GameType.SPECTATOR);
        beginTravel(session);
        body.sendSystemMessage(Component.translatable("duosight.started", options.seconds()));
        guest.sendSystemMessage(Component.translatable("duosight.started", options.seconds()));
        return true;
    }

    public static void input(Connection sender, Packets.Input packet) {
        Route route = ROUTES.get(sender);
        if (route != null && packet.valid() && route.policy.input(packet.epoch())
                && route.inputs.incrementAndGet() <= 512) {
            Packets.send(route.recipient, new Packets.ForwardedInput(packet));
        }
    }

    public static void pose(Connection sender, Packets.Pose packet) {
        Route route = ROUTES.get(sender);
        if (route != null && packet.valid() && route.policy.visual(packet.epoch(), packet.dimension())
                && route.visuals.incrementAndGet() <= 64
                && route.position.distanceToSqr(new Vec3(packet.x(), packet.y(), packet.z())) <= 128 * 128) {
            Packets.send(route.recipient, new Packets.ForwardedPose(packet));
        }
    }

    public static void cursor(Connection sender, Packets.Cursor packet) {
        Route route = ROUTES.get(sender);
        if (route != null && packet.valid() && route.policy.visual(packet.epoch(), packet.dimension())
                && packet.menuId() == route.menuId && route.visuals.incrementAndGet() <= 64) {
            Packets.send(route.recipient, new Packets.ForwardedCursor(packet));
        }
    }

    public static void menu(ServerPlayer sender, boolean open) {
        if (sender == null) {
            return;
        }
        Session session = MEMBERS.get(sender.getUUID());
        if (session == null || (sender == session.body ? session.bodyMenu : session.guestMenu) == open) {
            return;
        }
        if (sender == session.body) {
            session.bodyMenu = open;
        } else {
            session.guestMenu = open;
        }
        session.clock.invalidate();
        if (session.travel.waiting()) {
            session.travel.begin(session.clock.epoch(), session.dimension);
        }
        sync(session, false);
        if (!session.travel.waiting() && !session.endPending) {
            publishView(session, true);
        }
    }

    public static void ready(ServerPlayer sender, Packets.Ready packet) {
        if (sender == null) {
            return;
        }
        Session session = MEMBERS.get(sender.getUUID());
        if (session != null && !session.endPending && session.travel.waiting()
                && sender.serverLevel().dimension().location().toString().equals(packet.dimension())
                && session.travel.ready(sender == session.body, packet.epoch(), packet.dimension())) {
            sync(session, false);
            publishView(session, true);
        }
    }

    public static void view(ServerPlayer sender, Packets.View packet) {
        if (sender == null) {
            return;
        }
        Session session = MEMBERS.get(sender.getUUID());
        if (session != null && sender == session.body && packet.epoch() == session.clock.epoch()
                && session.viewsThisTick < 4 && packet.data().size() <= 12
                && packet.data().getString("menu").length() <= 256
                && packet.data().getString("title").length() <= 512) {
            session.viewsThisTick++;
            if (!session.bodyMenu && !session.travel.waiting() && !session.endPending) {
                session.screen = packet.data().copy();
                publishView(session, false);
                routes(session);
            }
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (UUID id : Set.copyOf(BOOK_REFRESHES)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                BOOK_REFRESHES.remove(id);
            } else if (BOOK_REQUESTS.computeIfAbsent(id, key -> new ActionCooldown(5))
                    .allow(server.getTickCount())) {
                BOOK_REFRESHES.remove(id);
                sendBook(player, true);
            }
        }
        for (Session session : new ArrayList<>(Set.copyOf(MEMBERS.values()))) {
            session.viewsThisTick = 0;
            if (session.body.hasDisconnected() || session.guest.hasDisconnected()
                    || session.body.isDeadOrDying()) {
                end(session, "duosight.ended");
                continue;
            }
            if (session.body.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                end(session, "duosight.ended");
                continue;
            }
            if (session.body.wonGame) {
                if (!session.endPending) {
                    session.endPending = true;
                    session.clock.invalidate();
                    session.travel.begin(session.clock.epoch(), session.dimension);
                    sync(session, false);
                }
                continue;
            }
            follow(session);
            boolean changed = !session.suspended() && session.clock.tick();
            if (changed || server.getTickCount() % 20 == 0) {
                sync(session, changed);
            }
            if (!session.travel.waiting()) {
                publishView(session, changed);
            }
            routes(session);
        }
        INVITES.values().removeIf(invite -> invite.deadline < server.getTickCount());
    }

    private static void publishView(Session session, boolean changed) {
        ServerPlayer body = session.body;
        session.maps.send(body, session.guest);
        CompoundTag data = session.screen.copy();
        data.putFloat("health", body.getHealth());
        data.putInt("food", body.getFoodData().getFoodLevel());
        data.putInt("level", body.experienceLevel);
        data.putInt("totalExperience", body.totalExperience);
        data.putFloat("experienceProgress", body.experienceProgress);
        data.putInt("selected", body.getInventory().selected);
        ListTag inventory = new ListTag();
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            inventory.add(save(body, body.getInventory().getItem(i)));
        }
        data.put("inventory", inventory);
        data.put("recipes", body.getRecipeBook().toNbt());
        var menu = body.containerMenu;
        if (!data.getString("menu").isEmpty() && data.getInt("id") == menu.containerId) {
            ListTag slots = new ListTag();
            menu.slots.forEach(slot -> slots.add(save(body, slot.getItem())));
            data.put("slots", slots);
            data.put("carried", save(body, menu.getCarried()));
            data.putIntArray("values", ((MenuAccess) menu).duosight$data().stream()
                    .mapToInt(slot -> (short) slot.get()).toArray());
        } else {
            data.putString("menu", "");
        }
        if (changed || !data.equals(session.previousView)) {
            Packets.send(session.guest, new Packets.ForwardedView(
                    new Packets.View(session.clock.epoch(), data)));
            session.previousView = data;
        }
    }

    private static CompoundTag save(ServerPlayer player, ItemStack item) {
        return item.isEmpty() ? new CompoundTag() : (CompoundTag) item.save(player.registryAccess());
    }

    private static void follow(Session session) {
        ServerPlayer body = session.body;
        ServerPlayer guest = session.guest;
        if (guest.serverLevel() != body.serverLevel()) {
            guest.setCamera(guest);
            guest.teleportTo(body.serverLevel(), body.getX(), body.getY(), body.getZ(),
                    Set.of(), body.getYRot(), body.getXRot());
        }
        if (guest.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            guest.setGameMode(GameType.SPECTATOR);
        }
        if (guest.getCamera() != body) {
            guest.setCamera(body);
        }
        guest.setDeltaMovement(Vec3.ZERO);
    }

    private static void sync(Session session, boolean changed) {
        var clock = session.clock;
        Packets.send(session.body, new Packets.State(true, true, clock.bodyControls(),
                session.body.getId(), clock.seconds(), clock.epoch(), changed, session.dimension,
                session.travel.waiting(), session.suspended()));
        Packets.send(session.guest, new Packets.State(true, false, !clock.bodyControls(),
                session.body.getId(), clock.seconds(), clock.epoch(), changed, session.dimension,
                session.travel.waiting(), session.suspended()));
        routes(session);
    }

    private static void routes(Session session) {
        boolean travelling = session.travel.waiting() || session.endPending;
        for (ServerPlayer sender : new ServerPlayer[]{session.body, session.guest}) {
            boolean body = sender == session.body;
            ROUTES.put(sender.connection.getConnection(), new Route(
                    new RelayPolicy(session.clock.epoch(), session.dimension, body,
                            !session.clock.bodyControls(), session.suspended(), travelling),
                    (body ? session.guest : session.body).connection.getConnection(),
                    session.body.position(), session.body.containerMenu.containerId,
                    new AtomicInteger(), new AtomicInteger()));
        }
    }

    private static void beginTravel(Session session) {
        session.endPending = false;
        session.dimension = session.body.serverLevel().dimension().location().toString();
        session.clock.invalidate();
        session.travel.begin(session.clock.epoch(), session.dimension);
        session.screen = new CompoundTag();
        session.maps.clear();
        session.previousView = null;
        follow(session);
        sync(session, false);
    }

    public static void stop(ServerPlayer player, String reason) {
        if (player == null) {
            return;
        }
        Session session = MEMBERS.get(player.getUUID());
        if (session != null) {
            end(session, reason);
        }
    }

    private static void end(Session session, String reason) {
        MEMBERS.remove(session.body.getUUID());
        MEMBERS.remove(session.guest.getUUID());
        ROUTES.remove(session.body.connection.getConnection());
        ROUTES.remove(session.guest.connection.getConnection());
        Packets.State inactive = new Packets.State(false, false, false, -1, 0, 0, false, "", false, false);
        if (!session.body.hasDisconnected()) {
            session.body.closeContainer();
            Packets.send(session.body, inactive);
            session.body.sendSystemMessage(Component.translatable(reason));
        }
        restore(session.guest);
        if (!session.guest.hasDisconnected()) {
            Packets.send(session.guest, inactive);
            session.guest.connection.send(new ClientboundSetExperiencePacket(
                    session.guest.experienceProgress, session.guest.totalExperience, session.guest.experienceLevel));
            session.guest.sendSystemMessage(Component.translatable(reason));
        }
    }

    private static void restore(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RESTORE)) {
            return;
        }
        CompoundTag saved = data.getCompound(RESTORE);
        var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(saved.getString("dimension")));
        var level = player.getServer().getLevel(key);
        player.setCamera(player);
        player.setGameMode(GameType.byId(saved.getInt("mode")));
        if (level != null) {
            player.teleportTo(level, saved.getDouble("x"), saved.getDouble("y"), saved.getDouble("z"),
                    Set.of(), saved.getFloat("yaw"), saved.getFloat("pitch"));
        }
        player.inventoryMenu.sendAllDataToRemote();
        data.remove(RESTORE);
        ((PlayerListAccess) player.getServer().getPlayerList()).duosight$save(player);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, "duosight.disconnected");
            INVITES.entrySet().removeIf(entry -> entry.getKey().equals(player.getUUID())
                    || entry.getValue().body.equals(player.getUUID()));
            BOOK_REQUESTS.remove(player.getUUID());
            BOOK_ACTIONS.remove(player.getUUID());
            BOOK_REFRESHES.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !MEMBERS.containsKey(player.getUUID())) {
            restore(player);
            GuideBook.login(player);
        }
    }

    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = MEMBERS.get(player.getUUID());
            if (session != null && player == session.body) {
                beginTravel(session);
            }
        }
    }

    @SubscribeEvent
    public void clonePlayer(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        if (original.contains(RESTORE)) {
            event.getEntity().getPersistentData().put(RESTORE, original.getCompound(RESTORE).copy());
        }
        if (original.contains(GuideBook.DATA)) {
            event.getEntity().getPersistentData().put(GuideBook.DATA, original.getCompound(GuideBook.DATA).copy());
        }
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = MEMBERS.get(player.getUUID());
            if (session != null && event.isEndConquered()) {
                if (player.getUUID().equals(session.body.getUUID())) {
                    session.body = player;
                } else {
                    session.guest = player;
                }
                beginTravel(session);
            }
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, "duosight.died");
        }
    }

    @SubscribeEvent
    public void stopping(ServerStoppingEvent event) {
        for (Session session : Set.copyOf(MEMBERS.values())) {
            end(session, "duosight.ended");
        }
        INVITES.clear();
        ROUTES.clear();
        BOOK_REQUESTS.clear();
        BOOK_ACTIONS.clear();
        BOOK_REFRESHES.clear();
    }

    private record Invite(UUID body, long deadline, PairingOptions options) {}
    private record Route(RelayPolicy policy, Connection recipient, Vec3 position, int menuId,
                         AtomicInteger inputs, AtomicInteger visuals) {}

    private static final class Session {
        ServerPlayer body;
        ServerPlayer guest;
        final SwapClock clock;
        final TravelGate travel = new TravelGate();
        final SharedMaps maps = new SharedMaps();
        String dimension;
        boolean bodyMenu, guestMenu, endPending;
        int viewsThisTick;
        CompoundTag screen = new CompoundTag();
        CompoundTag previousView;

        Session(ServerPlayer body, ServerPlayer guest, PairingOptions options) {
            this.body = body;
            this.guest = guest;
            this.clock = new SwapClock(options.seconds(), options.driver());
        }

        boolean suspended() {
            return bodyMenu || guestMenu || endPending || travel.waiting();
        }
    }
}
