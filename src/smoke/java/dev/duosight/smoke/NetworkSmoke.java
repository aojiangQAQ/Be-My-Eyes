package dev.duosight.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.duosight.DuoConfig;
import dev.duosight.client.DuoClient;
import dev.duosight.core.SwapClock;
import dev.duosight.net.Packets;
import dev.duosight.mixin.KeyboardHandlerAccess;
import dev.duosight.mixin.MouseHandlerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.SystemMessageReceivedEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "duosight_smoke", value = Dist.CLIENT)
public final class NetworkSmoke {
    private static final String ROLE = System.getProperty("duosight.networkSmoke", "");
    private static final boolean HOST = ROLE.equals("host");
    private static final boolean CURSOR_SMOKE = Boolean.getBoolean("duosight.cursorSmoke");
    private static final boolean COMMAND_SMOKE = Boolean.getBoolean("duosight.commandSmoke");
    private static final boolean CONTAINER_SMOKE = Boolean.getBoolean("duosight.containerSmoke");
    private static final Set<UUID> ACKS = new HashSet<>();
    private static int serverStage, serverWait, frozenSeconds, frozenEpoch;
    private static int clientStage, clientTicks, stableTicks;
    private static boolean started, published, acted, acknowledged, stopped;
    private static long deadline = System.nanoTime() + 600_000_000_000L;
    private static double startZ;
    private static int originalGuestItems;
    private static int cursorStep, cursorWait;
    private static boolean sized;
    private static boolean languageChecked;
    private static boolean initialChatOpened, initialChatReopened, initialChatClosed;

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        if (!HOST) {
            return;
        }
        event.getDispatcher().register(Commands.literal("duosmoke")
                .then(Commands.argument("stage", IntegerArgumentType.integer()).executes(context -> {
                    if (IntegerArgumentType.getInteger(context, "stage") == serverStage) {
                        ACKS.add(context.getSource().getPlayerOrException().getUUID());
                    }
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void message(SystemMessageReceivedEvent event) {
        if (ROLE.isEmpty()) {
            return;
        }
        String text = event.getMessage().getString();
        if (CONTAINER_SMOKE && text.startsWith("BMECONTAINER:")) {
            ContainerSmoke.expected(text.substring(13));
            event.setCanceled(true);
            return;
        }
        if (text.startsWith("DUOSMOKE:")) {
            clientStage = Integer.parseInt(text.substring(9));
            clientTicks = 0;
            stableTicks = 0;
            acted = acknowledged = false;
            cursorStep = cursorWait = 0;
            sized = false;
            ContainerSmoke.reset();
            ChatSmoke.reset();
            event.setCanceled(true);
        }
    }

    private static Packets.State state() throws Exception {
        Field field = DuoClient.class.getDeclaredField("state");
        field.setAccessible(true);
        return (Packets.State) field.get(null);
    }

    @SubscribeEvent
    public static void client(TickEvent.ClientTickEvent.Post event) throws Exception {
        if (ROLE.isEmpty() || stopped) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        check(System.nanoTime() < deadline, "client timed out, stage " + clientStage);
        if (!started && mc.isGameLoadFinished() && mc.getOverlay() == null
                && !(mc.screen instanceof TitleScreen)) {
            mc.setScreen(new TitleScreen(false));
            return;
        }
        if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            if (!languageChecked) {
                LanguageSmoke.validate(mc);
                languageChecked = true;
            }
            mc.options.renderDistance().set(2);
            mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60);
            mc.options.pauseOnLostFocus = false;
            if (HOST) {
                started = true;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel("network-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("Be My Eyes Tests", GameType.SURVIVAL, false,
                                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            } else if (Files.exists(Path.of("../run-network-host/ready.txt"))) {
                started = true;
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("127.0.0.1:25579"),
                        new ServerData("Be My Eyes tests", "127.0.0.1:25579", ServerData.Type.OTHER), false, null);
            }
            return;
        }
        if (HOST && !published && mc.player != null && mc.screen == null) {
            published = true;
            var server = mc.getSingleplayerServer();
            check(server.publishServer(GameType.SURVIVAL, true, 25579), "LAN publish");
            server.setUsesAuthentication(false);
            Files.writeString(Path.of("ready.txt"), "ready");
        }
        if (COMMAND_SMOKE && HOST && published && !initialChatOpened && !DuoClient.active()
                && mc.player != null && clientStage == 0) {
            mc.options.chatHeightFocused().set(0.2);
            mc.setScreen(new ChatScreen("BME_BEFORE_SESSION"));
            initialChatOpened = true;
        }
        if (mc.player == null || mc.level == null || clientStage == 0 || acknowledged) {
            return;
        }
        clientTicks++;
        Packets.State state = state();
        if (clientStage == 99) {
            System.out.println("DUOSIGHT_NETWORK_SMOKE_PASS: " + ROLE);
            stopped = true;
            mc.stop();
            return;
        }
        if (clientStage == 17) {
            if (!state.active() && clientTicks > 10) {
                check(mc.player.isSpectator() == false, "normal player mode restored");
                check(!DuoClient.key(GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0), "normal input restored");
                if (!HOST) {
                    check(mc.player.getInventory().getItem(0).is(Items.DIAMOND)
                            && mc.player.getInventory().getItem(0).getCount() == 7,
                            "original client inventory restored");
                    if (CONTAINER_SMOKE) {
                        check(mc.player.experienceLevel == 7, "original client experience restored");
                    }
                }
                ack(mc);
            }
            return;
        }
        if (clientStage == 16) {
            if (!HOST && state.active() && !acted) {
                DuoClient.key(GLFW.GLFW_KEY_F8, 0, GLFW.GLFW_PRESS, 0);
                acted = true;
            }
            ack(mc);
            return;
        }
        if (!state.active()) {
            return;
        }
        if (HOST && clientStage == 1 && initialChatOpened && !initialChatClosed) {
            if (clientTicks > 12 && mc.screen == null && !state.travelling() && !initialChatReopened) {
                pressKey(mc, GLFW.GLFW_KEY_T);
                initialChatReopened = true;
                return;
            }
            if (clientTicks > 20 && state.suspended()) {
                check(DuoClient.personalMenu() && mc.screen instanceof ChatScreen,
                        "native chat is a personal menu immediately after pairing starts");
                check(!DuoClient.character('a', 0), "native chat accepts local typing");
                save(mc, "chat-at-session-start.png");
                mc.screen.onClose();
                initialChatClosed = true;
            }
            return;
        }
        if (CONTAINER_SMOKE && clientStage >= 100 && clientStage < 100 + ContainerSmoke.COUNT * 3) {
            if (ContainerSmoke.client(mc, state, clientStage, clientTicks)) {
                ack(mc);
            }
            return;
        }
        if (CURSOR_SMOKE && clientStage >= 30 && clientStage <= 38) {
            cursorSmoke(mc, state);
            return;
        }
        if (COMMAND_SMOKE && clientStage >= 40 && clientStage <= 46) {
            commandSmoke(mc, state);
            return;
        }
        if (COMMAND_SMOKE && clientStage >= 50 && clientStage <= 64) {
            if (ChatSmoke.client(mc, state, clientStage, clientTicks, HOST)) {
                ack(mc);
            }
            return;
        }
        if (clientStage == 2 || clientStage == 4 || clientStage == 13) {
            boolean opener = clientStage != 4 ? HOST : !HOST;
            if (opener && !acted) {
                check(DuoClient.key(GLFW.GLFW_KEY_ESCAPE, 0, GLFW.GLFW_PRESS, 0), "ESC interception");
                check(mc.screen instanceof PauseScreen, "native pause screen");
                acted = true;
            }
            if (opener && clientTicks == 5 && clientStage != 13) {
                mc.setScreen(new OptionsScreen(mc.screen, mc.options));
            }
            if (state.suspended() && clientTicks > 10) {
                check(DuoClient.active(), "menu preserves session");
                check(DuoClient.personalMenu() == opener, "personal menus stay local");
                if (opener) {
                    check(!DuoClient.button(0, GLFW.GLFW_RELEASE, 0), "menu mouse enabled");
                    save(mc, "menu-stage-" + clientStage + ".png");
                }
                ack(mc);
            }
            return;
        }
        if (clientStage == 3 || clientStage == 5 || clientStage == 14) {
            boolean closer = clientStage != 5 ? HOST : !HOST;
            if (closer && !acted) {
                mc.screen.onClose();
                if (mc.screen instanceof PauseScreen) {
                    mc.screen.onClose();
                }
                acted = true;
            }
            if (!state.suspended() && clientTicks > 10) {
                check(!DuoClient.personalMenu(), "menu closed");
                if (clientStage == 14) {
                    check(mc.screen instanceof InventoryScreen, "shared container restored after personal menu");
                }
                ack(mc);
            }
            return;
        }
        if (state.suspended() || state.travelling()) {
            stableTicks = 0;
            return;
        }
        String expectedDimension = switch (clientStage) {
            case 6, 18 -> "minecraft:the_nether";
            case 8, 20 -> "minecraft:the_end";
            default -> "minecraft:overworld";
        };
        if (!state.dimension().equals(expectedDimension)
                || !mc.level.dimension().location().toString().equals(expectedDimension)) {
            return;
        }
        if (clientStage == 10 && state.driver() != !HOST) {
            return;
        }
        stableTicks++;
        if (clientStage == 11) {
            if (!HOST) {
                if (!acted) {
                    startZ = mc.gameRenderer.getMainCamera().getPosition().z;
                    DuoClient.key(GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0);
                    acted = true;
                }
                if (clientTicks == 15) {
                    DuoClient.key(GLFW.GLFW_KEY_W, 0, GLFW.GLFW_RELEASE, 0);
                }
                if (clientTicks > 30) {
                    check(Math.abs(mc.gameRenderer.getMainCamera().getPosition().z - startZ) > 0.1,
                            "remote driver moves shared body");
                    ack(mc);
                }
            } else if (clientTicks > 30) {
                ack(mc);
            }
            return;
        }
        if (clientStage == 12) {
            if (!HOST && !acted) {
                DuoClient.key(GLFW.GLFW_KEY_E, 0, GLFW.GLFW_PRESS, 0);
                DuoClient.key(GLFW.GLFW_KEY_E, 0, GLFW.GLFW_RELEASE, 0);
                acted = true;
            }
            if (mc.screen instanceof InventoryScreen && stableTicks > 10) {
                ack(mc);
            }
            return;
        }
        if (clientStage == 15) {
            if (!HOST && !acted) {
                DuoClient.key(GLFW.GLFW_KEY_ESCAPE, 0, GLFW.GLFW_PRESS, 0);
                DuoClient.key(GLFW.GLFW_KEY_ESCAPE, 0, GLFW.GLFW_RELEASE, 0);
                acted = true;
            }
            if (mc.screen == null && stableTicks > 10) {
                check(DuoClient.active(), "container ESC preserves session");
                ack(mc);
            }
            return;
        }
        if (stableTicks < 15) {
            return;
        }
        if (!HOST) {
            check(mc.getCameraEntity() != mc.player && mc.getCameraEntity().getId() == state.bodyId(),
                    "first person bound to shared body, stage " + clientStage);
            if (DuoClient.cameraPose() == null && stableTicks < 200) {
                return;
            }
            check(DuoClient.cameraPose() != null, "high frequency camera relay, stage " + clientStage);
        }
        if (state.driver()) {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                VisualSmoke.checkDriverFrame(mc, image);
                save(mc, "driver-countdown-stage-" + clientStage + ".png");
            }
        }
        System.out.println("DUOSIGHT_NETWORK_STAGE_OK: " + ROLE + " " + clientStage);
        ack(mc);
    }

    private static void ack(Minecraft mc) {
        acknowledged = true;
        mc.player.connection.sendCommand("duosmoke " + clientStage);
    }

    @SubscribeEvent
    public static void server(TickEvent.ServerTickEvent.Post event) throws Exception {
        if (!HOST) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerPlayer body = server.getPlayerList().getPlayerByName("DuoHost");
        ServerPlayer guest = server.getPlayerList().getPlayerByName("DuoGuest");
        if (body == null || guest == null) {
            return;
        }
        if (serverStage == 0) {
            body.setInvulnerable(true);
            guest.setInvulnerable(true);
            guest.teleportTo(body.serverLevel(), body.getX() + 2, body.getY(), body.getZ(), Set.of(), 0, 0);
            guest.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            if (CONTAINER_SMOKE) {
                guest.setExperienceLevels(7);
            }
            guest.inventoryMenu.sendAllDataToRemote();
            originalGuestItems = guest.getInventory().getItem(0).getCount();
            DuoConfig.SWAP_SECONDS.set(120);
            server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes invite DuoGuest");
            server.getCommands().performPrefixedCommand(guest.createCommandSourceStack(), "bemyeyes accept");
            stage(server, 1);
            return;
        }
        if (CONTAINER_SMOKE && serverStage >= 100 && serverStage < 100 + ContainerSmoke.COUNT * 3) {
            ContainerSmoke.server(body, guest, serverStage, serverWait++);
        }
        if (ACKS.size() < 2) {
            return;
        }
        if (COMMAND_SMOKE && serverStage >= 50 && serverStage <= 64) {
            if (serverStage == 50 || serverStage == 53 || serverStage == 54 || serverStage == 55
                    || serverStage == 57 || serverStage == 61) {
                SwapClock clock = clock(body);
                if (serverWait++ == 0) {
                    frozenSeconds = clock.seconds();
                    frozenEpoch = clock.epoch();
                }
                check(clock.seconds() == frozenSeconds && clock.epoch() == frozenEpoch,
                        "chat suspends clock without ending session");
                if (serverWait < 10) {
                    return;
                }
            }
            if (serverStage == 52) {
                server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes swap");
            } else if (serverStage == 59) {
                server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes swap");
            } else if (serverStage == 62) {
                body.closeContainer();
            } else if (serverStage == 64) {
                System.out.println("BE_MY_EYES_CHAT_SMOKE_PASS: server");
                if (CONTAINER_SMOKE) {
                    server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes interval 3600");
                    ContainerSmoke.open(body, ContainerSmoke.START);
                    stage(server, 100 + ContainerSmoke.START * 3);
                } else {
                    stage(server, CURSOR_SMOKE ? 30 : 2);
                }
                return;
            }
            stage(server, serverStage + 1);
            return;
        }
        if (CONTAINER_SMOKE && (serverStage == 1 && !COMMAND_SMOKE
                || serverStage >= 100 && serverStage < 100 + ContainerSmoke.COUNT * 3)) {
            if (serverStage == 1) {
                server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes interval 3600");
                ContainerSmoke.open(body, ContainerSmoke.START);
                stage(server, 100 + ContainerSmoke.START * 3);
            } else {
                int index = (serverStage - 100) / 3;
                int phase = (serverStage - 100) % 3;
                if (phase == 0) {
                    server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes swap");
                    ContainerSmoke.refill(body, index);
                    stage(server, serverStage + 1);
                } else if (phase == 1) {
                    stage(server, serverStage + 1);
                } else if (index + 1 < ContainerSmoke.COUNT) {
                    server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes swap");
                    ContainerSmoke.open(body, index + 1);
                    stage(server, serverStage + 1);
                } else {
                    server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes swap");
                    server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "duosight interval 120");
                    System.out.println("BE_MY_EYES_CONTAINER_SMOKE_PASS: server");
                    stage(server, CURSOR_SMOKE ? 30 : 2);
                }
            }
            return;
        }
        if (COMMAND_SMOKE && (serverStage == 1 || (serverStage >= 40 && serverStage <= 46))) {
            if (serverStage == 1) {
                try {
                    server.getCommands().getDispatcher().execute("duosight interval 15",
                            server.createCommandSourceStack());
                    throw new AssertionError("console interval accepted without player session");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                    check(clock(body).intervalSeconds() == 120, "console cannot change player session");
                }
                stage(server, 40);
            } else if (serverStage == 40 || serverStage == 42) {
                SwapClock clock = clock(body);
                if (serverWait++ == 0) {
                    frozenSeconds = clock.seconds();
                    frozenEpoch = clock.epoch();
                }
                check(clock.seconds() == frozenSeconds && clock.epoch() == frozenEpoch,
                        "command input suspends clock");
                if (serverWait >= 20) {
                    stage(server, serverStage + 1);
                }
            } else if (serverStage == 41) {
                check(clock(body).intervalSeconds() == 15 && clock(body).bodyControls(),
                        "interval command preserves roles");
                for (String argument : new String[]{"14", "3601", "invalid"}) {
                    try {
                        server.getCommands().getDispatcher().execute("duosight interval " + argument,
                                guest.createCommandSourceStack().withPermission(0));
                        throw new AssertionError("invalid interval accepted: " + argument);
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                        check(clock(body).intervalSeconds() == 15, "invalid interval leaves clock unchanged");
                    }
                }
                stage(server, 42);
            } else if (serverStage == 43) {
                check(!clock(body).bodyControls() && clock(body).intervalSeconds() == 15,
                        "observer command swaps roles");
                stage(server, 44);
            } else if (serverStage == 44) {
                check(!clock(body).bodyControls() && clock(body).intervalSeconds() == 30,
                        "remote driver command updates interval");
                stage(server, 45);
            } else if (serverStage == 45) {
                check(clock(body).bodyControls(), "body observer command swaps roles back");
                stage(server, 46);
            } else {
                check(clock(body).bodyControls() && clock(body).intervalSeconds() == 120,
                        "default interval restored for dimension regression");
                System.out.println("DUOSIGHT_COMMAND_SMOKE_PASS: server");
                stage(server, 50);
            }
            return;
        }
        if (CURSOR_SMOKE && (serverStage == 1 || serverStage >= 30 && serverStage <= 38
                || (!COMMAND_SMOKE && !CONTAINER_SMOKE && (serverStage == 16 || serverStage == 17)))) {
            if (serverStage == 1) {
                stage(server, 30);
            } else if (serverStage == 30 || serverStage == 32
                    || serverStage == 34 || serverStage == 36) {
                stage(server, serverStage + 1);
            } else if (serverStage == 31 || serverStage == 35) {
                var pos = body.blockPosition().offset(1, 0, 0);
                body.closeContainer();
                body.serverLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
                body.openMenu(new SimpleMenuProvider((id, inventory, player) ->
                        new CraftingMenu(id, inventory, ContainerLevelAccess.create(body.serverLevel(), pos)),
                        Component.translatable("container.crafting")));
                stage(server, serverStage + 1);
            } else if (serverStage == 33) {
                body.closeContainer();
                Field remaining = SwapClock.class.getDeclaredField("remaining");
                remaining.setAccessible(true);
                remaining.setInt(clock(body), 1);
                stage(server, 34);
            } else if (serverStage == 37) {
                stage(server, 38);
            } else if (serverStage == 38) {
                if (COMMAND_SMOKE || CONTAINER_SMOKE) {
                    if (!clock(body).bodyControls()) {
                        server.getCommands().performPrefixedCommand(body.createCommandSourceStack(), "bemyeyes swap");
                    }
                    stage(server, 2);
                } else {
                    stage(server, 16);
                }
            } else if (serverStage == 16) {
                stage(server, 17);
            } else if (serverStage == 17) {
                check(guest.getInventory().getItem(0).is(Items.DIAMOND)
                        && guest.getInventory().getItem(0).getCount() == originalGuestItems,
                        "guest inventory retained after recipe screens");
                stage(server, 99);
            }
            return;
        }
        if (serverStage == 2 || serverStage == 4 || serverStage == 13) {
            SwapClock clock = clock(body);
            if (serverWait++ == 0) {
                frozenSeconds = clock.seconds();
                frozenEpoch = clock.epoch();
            }
            check(clock.seconds() == frozenSeconds && clock.epoch() == frozenEpoch, "menu suspends clock");
            if (serverWait < 40) {
                return;
            }
        }
        if (serverStage == 5) {
            travel(body, Level.NETHER);
            if (COMMAND_SMOKE) {
                int epoch = clock(body).epoch();
                check(server.getCommands().getDispatcher().execute("duosight swap",
                        guest.createCommandSourceStack().withPermission(0)) == 0,
                        "manual swap rejected during dimension loading");
                check(clock(body).epoch() == epoch, "rejected swap preserves loading epoch");
            }
        } else if (serverStage == 6) {
            travel(body, Level.OVERWORLD);
        } else if (serverStage == 7) {
            travel(body, Level.END);
        } else if (serverStage == 8) {
            body.showEndCredits();
        } else if (serverStage == 9) {
            check(server.getPlayerList().getPlayer(body.getUUID()) == body, "End body replacement");
            SwapClock clock = clock(body);
            Field remaining = SwapClock.class.getDeclaredField("remaining");
            remaining.setAccessible(true);
            remaining.setInt(clock, 1);
        } else if (serverStage == 15) {
            travel(body, Level.NETHER);
            stage(server, 18);
            return;
        } else if (serverStage == 18) {
            travel(body, Level.OVERWORLD);
        } else if (serverStage == 19) {
            travel(body, Level.END);
        } else if (serverStage == 20) {
            body.showEndCredits();
        } else if (serverStage == 21) {
            stage(server, 16);
            return;
        } else if (serverStage == 17) {
            check(guest.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "guest restored after dimensions");
            check(guest.getInventory().getItem(0).is(Items.DIAMOND)
                    && guest.getInventory().getItem(0).getCount() == originalGuestItems, "guest inventory retained");
            if (COMMAND_SMOKE) {
                check(server.getCommands().getDispatcher().execute("duosight swap",
                        guest.createCommandSourceStack().withPermission(0)) == 0,
                        "swap rejected outside a session");
                check(server.getCommands().getDispatcher().execute("duosight interval 60",
                        body.createCommandSourceStack().withPermission(0)) == 0,
                        "interval rejected outside a session");
            }
            stage(server, 99);
            return;
        }
        stage(server, serverStage + 1);
    }

    private static SwapClock clock(ServerPlayer body) throws Exception {
        Field members = Class.forName("dev.duosight.server.Sessions").getDeclaredField("MEMBERS");
        members.setAccessible(true);
        Object session = ((Map<?, ?>) members.get(null)).get(body.getUUID());
        check(session != null, "server session retained");
        Field clock = session.getClass().getDeclaredField("clock");
        clock.setAccessible(true);
        return (SwapClock) clock.get(session);
    }

    private static void travel(ServerPlayer body, net.minecraft.resources.ResourceKey<Level> dimension) {
        var level = body.getServer().getLevel(dimension);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.BEDROCK.defaultBlockState());
                for (int y = 80; y < 84; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        body.teleportTo(level, 0.5, 80, 0.5, Set.of(), 0, 0);
    }

    private static void stage(MinecraftServer server, int next) {
        serverStage = next;
        serverWait = 0;
        ACKS.clear();
        server.getPlayerList().getPlayers().forEach(player ->
                player.sendSystemMessage(Component.literal("DUOSMOKE:" + next)));
        System.out.println("DUOSIGHT_NETWORK_STAGE: " + next);
    }

    private static void save(Minecraft mc, String name) throws Exception {
        Files.createDirectories(Path.of("smoke-screenshots"));
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(Path.of("smoke-screenshots", name));
        }
    }

    private static EditBox commandInput(Minecraft mc) throws Exception {
        Field field = ChatScreen.class.getDeclaredField("input");
        field.setAccessible(true);
        return (EditBox) field.get(mc.screen);
    }

    private static void commandSmoke(Minecraft mc, Packets.State state) throws Exception {
        boolean opener = clientStage == 40 || clientStage == 41
                || clientStage == 45 || clientStage == 46 ? HOST : !HOST;
        if (clientStage == 40 || clientStage == 42) {
            if (opener && !acted) {
                int key = clientStage == 40 ? GLFW.GLFW_KEY_SLASH : GLFW.GLFW_KEY_T;
                mc.options.chatHeightFocused().set(0.2);
                ((KeyboardHandlerAccess) mc.keyboardHandler).duosight$key(
                        mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0);
                check(mc.screen instanceof ChatScreen, "native chat screen opened");
                ((KeyboardHandlerAccess) mc.keyboardHandler).duosight$character(
                        mc.getWindow().getWindow(), key == GLFW.GLFW_KEY_T ? 't' : '/', 0);
                check(commandInput(mc).getValue().equals(clientStage == 40 ? "/" : ""),
                        "opening character not duplicated");
                ((KeyboardHandlerAccess) mc.keyboardHandler).duosight$key(
                        mc.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0);
                acted = true;
            }
            if (state.suspended() && clientTicks > 10) {
                check(DuoClient.personalMenu() == opener, "command entry remains local");
                if (opener) {
                    check(mc.screen instanceof ChatScreen, "command input stays visible");
                    save(mc, "command-entry-stage-" + clientStage + ".png");
                    if (state.driver()) try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        double scale = image.getHeight() / (double) mc.getWindow().getGuiScaledHeight();
                        for (int y = (int) (22 * scale); y < 26 * scale; y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                check((image.getPixelRGBA(x, y) & 0xFFFFFF) == 0,
                                        "command backdrop hidden at " + x + "," + y);
                            }
                        }
                    }
                }
                ack(mc);
            }
            return;
        }
        if (opener && !acted) {
            if (!(mc.screen instanceof ChatScreen)) {
                pressKey(mc, GLFW.GLFW_KEY_SLASH);
            }
            String command = switch (clientStage) {
                case 41 -> "/bemyeyes interval 15";
                case 44 -> "/duosight interval 30";
                case 46 -> "/bemyeyes interval 120";
                default -> "/bemyeyes swap";
            };
            commandInput(mc).setValue(command);
            mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            if (mc.screen instanceof ChatScreen) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            }
            check(!(mc.screen instanceof ChatScreen), "command submitted");
            acted = true;
        }
        boolean guestControls = clientStage >= 43 && clientStage <= 44;
        int interval = clientStage <= 43 ? 15 : clientStage <= 45 ? 30 : 120;
        if (!state.suspended() && !state.travelling() && clientTicks > 10
                && state.driver() == (guestControls ? !HOST : HOST)
                && state.seconds() <= interval && state.seconds() > interval - 5) {
            if (state.driver()) {
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    VisualSmoke.checkDriverFrame(mc, image);
                }
                save(mc, "command-countdown-stage-" + clientStage + ".png");
            }
            System.out.println("DUOSIGHT_COMMAND_STAGE_PASS: " + ROLE + " " + clientStage);
            ack(mc);
        }
    }

    private static void cursorSmoke(Minecraft mc, Packets.State state) throws Exception {
        if (state.suspended() || state.travelling() || state.driver() != (clientStage < 34 ? HOST : !HOST)) {
            return;
        }
        if (clientStage == 38) {
            if (state.driver() && mc.screen != null && !acted) {
                pressKey(mc, GLFW.GLFW_KEY_ESCAPE);
                acted = true;
            }
            if (mc.screen == null && clientTicks > 10) {
                ack(mc);
            }
            return;
        }
        boolean inventory = clientStage == 30 || clientStage == 31 || clientStage == 34 || clientStage == 35;
        boolean wide = clientStage % 2 == 1;
        if (!acted) {
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), wide ? 1024 : 640, wide ? 768 : 480);
            acted = true;
            return;
        }
        if (!sized) {
            if (clientTicks < 10) {
                return;
            }
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            if (state.driver() && inventory && !(mc.screen instanceof InventoryScreen)) {
                pressKey(mc, GLFW.GLFW_KEY_E);
            }
            sized = true;
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)
                || inventory != (screen instanceof InventoryScreen)
                || !(screen instanceof RecipeUpdateListener recipes)) {
            return;
        }
        RecipeBookComponent book = recipes.getRecipeBookComponent();
        check((screen.width >= 379) == wide, "correct wide/narrow recipe-book layout");
        if (!book.isVisible()) {
            if (state.driver() && clientTicks % 5 == 0) {
                ImageButton button = (ImageButton) screen.children().stream()
                        .filter(ImageButton.class::isInstance).findFirst().orElseThrow();
                moveMouse(mc, button.getX() + button.getWidth() / 2.0,
                        button.getY() + button.getHeight() / 2.0);
                click(mc);
            }
            return;
        }
        int[] dx = {73, 55, 30};
        int[] dy = {145, 18, 60};
        if (cursorStep >= dx.length) {
            System.out.println("DUOSIGHT_RECIPE_CURSOR_PASS: " + ROLE + " stage " + clientStage
                    + " " + screen.width + "x" + screen.height);
            ack(mc);
            return;
        }
        Field offset = RecipeBookComponent.class.getDeclaredField("xOffset");
        offset.setAccessible(true);
        int x = (screen.width - 147) / 2 - offset.getInt(book) + dx[cursorStep];
        int y = (screen.height - 166) / 2 + dy[cursorStep];
        if (state.driver()) {
            moveMouse(mc, x, y);
        } else {
            double actualX = mc.mouseHandler.xpos() * screen.width / mc.getWindow().getScreenWidth();
            double actualY = mc.mouseHandler.ypos() * screen.height / mc.getWindow().getScreenHeight();
            if (Math.abs(actualX - x) >= 2 || Math.abs(actualY - y) >= 2) {
                cursorWait = 0;
                return;
            }
        }
        cursorWait++;
        if (cursorWait < (state.driver() ? 40 : 12)) {
            return;
        }
        if (!state.driver()) {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int px = (int) (x * (double) image.getWidth() / screen.width);
                int py = (int) (y * (double) image.getHeight() / screen.height);
                int green = 0;
                for (int yy = Math.max(0, py - 6); yy <= Math.min(image.getHeight() - 1, py + 6); yy++) {
                    for (int xx = Math.max(0, px - 6); xx <= Math.min(image.getWidth() - 1, px + 6); xx++) {
                        if ((image.getPixelRGBA(xx, yy) & 0xFFFFFF) == 0xB8E665) {
                            green++;
                        }
                    }
                }
                Files.createDirectories(Path.of("smoke-screenshots"));
                image.writeToFile(Path.of("smoke-screenshots",
                        "recipe-cursor-" + clientStage + "-" + cursorStep + ".png"));
                check(green >= 5, "cursor visible above recipe book, stage " + clientStage
                        + " point " + cursorStep + ", green pixels " + green);
                double cursorX = mc.mouseHandler.xpos() * screen.width / mc.getWindow().getScreenWidth();
                double cursorY = mc.mouseHandler.ypos() * screen.height / mc.getWindow().getScreenHeight();
                check(Math.abs(cursorX - x) < 2 && Math.abs(cursorY - y) < 2,
                        "recipe cursor coordinates agree");
            }
        }
        cursorWait = 0;
        cursorStep++;
    }

    private static void pressKey(Minecraft mc, int key) {
        var keyboard = (KeyboardHandlerAccess) mc.keyboardHandler;
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0);
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void moveMouse(Minecraft mc, double x, double y) {
        ((MouseHandlerAccess) mc.mouseHandler).duosight$move(mc.getWindow().getWindow(),
                x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
    }

    private static void click(Minecraft mc) {
        var mouse = (MouseHandlerAccess) mc.mouseHandler;
        mouse.duosight$press(mc.getWindow().getWindow(), 0, GLFW.GLFW_PRESS, 0);
        mouse.duosight$press(mc.getWindow().getWindow(), 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Be My Eyes network test: " + message);
        }
    }
}
