package dev.duosight.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.duosight.client.DuoClient;
import dev.duosight.client.GuideClient;
import dev.duosight.client.GuideScreen;
import dev.duosight.core.GuidePages;
import dev.duosight.core.GuideAction;
import dev.duosight.core.PairingOptions;
import dev.duosight.core.SwapClock;
import dev.duosight.mixin.KeyboardHandlerAccess;
import dev.duosight.mixin.MouseHandlerAccess;
import dev.duosight.net.Packets;
import dev.duosight.server.GuideBook;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class GuideSmoke {
    private static boolean acted, changed, fitted;
    private static int hostSpam, guestSpam;

    static void reset() {
        acted = changed = fitted = false;
    }

    static void prepare(ServerPlayer host, ServerPlayer guest) {
        host.getServer().getPlayerList().setAllowCommandsForAllPlayers(false);
        check(!host.getServer().getPlayerList().isOp(guest.getGameProfile()),
                "guest tests run without operator permissions");
        for (ServerPlayer player : new ServerPlayer[]{host, guest}) {
            check(books(player) == 1, "automatic first-login book: " + player.getName().getString());
            GuideBook.login(player);
            GuideBook.login(player);
            check(books(player) == 1, "login does not duplicate books");
        }
    }

    private static long books(ServerPlayer player) {
        return java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                .filter(slot -> player.getInventory().getItem(slot).is(GuideBook.ITEM.get())).count()
                + player.inventoryMenu.slots.stream().filter(slot ->
                        slot.container instanceof CraftingContainer && slot.getItem().is(GuideBook.ITEM.get())).count()
                + (player.containerMenu == player.inventoryMenu ? 0 : player.containerMenu.slots.stream()
                        .filter(slot -> slot.container instanceof CraftingContainer
                                && slot.getItem().is(GuideBook.ITEM.get())).count())
                + (player.containerMenu.getCarried().is(GuideBook.ITEM.get()) ? 1 : 0);
    }

    static void restored(ServerPlayer host, ServerPlayer guest) {
        for (ServerPlayer player : new ServerPlayer[]{host, guest}) {
            check(GuideBook.played(player) && books(player) == 0,
                    "books remain consumed after stopping and returning from the End");
            GuideBook.login(player);
            check(GuideBook.give(player) == 0 && books(player) == 0,
                    "played players do not receive login or replacement books");
            int redstone = player.getInventory().items.stream().filter(item -> item.is(Items.REDSTONE))
                    .mapToInt(ItemStack::getCount).sum();
            var menu = new CraftingMenu(99, player.getInventory(),
                    ContainerLevelAccess.create(player.serverLevel(), player.blockPosition()));
            player.containerMenu = menu;
            menu.getSlot(1).set(new ItemStack(GuideBook.ITEM.get()));
            menu.getSlot(2).set(new ItemStack(Items.REDSTONE, 3));
            GuideBook.login(player);
            check(!menu.getSlot(1).hasItem() && menu.getSlot(2).getItem().is(Items.REDSTONE),
                    "login clears crafting-table guide books without removing ordinary materials");
            player.closeContainer();
            check(books(player) == 0 && player.getInventory().items.stream()
                            .filter(item -> item.is(Items.REDSTONE)).mapToInt(ItemStack::getCount).sum() == redstone + 3,
                    "crafting-table materials return without restoring guide books");
        }
        System.out.println("BE_MY_EYES_GUIDE_REMOVAL_PASS: server");
    }

    static boolean client(Minecraft mc, Packets.State state, int stage, int ticks, boolean host) throws Exception {
        if (ticks % 120 == 0) {
            System.out.println("BE_MY_EYES_GUIDE_WAIT: " + (host ? "host" : "guest") + " " + stage
                    + " " + (mc.screen == null ? "world" : mc.screen.getClass().getSimpleName()));
        }
        if (stage == 70) {
            if (!acted) {
                if (mc.screen instanceof ReceivingLevelScreen) {
                    return false;
                }
                bind(GLFW.GLFW_KEY_F9);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), host ? 1024 : 640, host ? 768 : 480);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                if (host) {
                    check(mc.player.getMainHandItem().is(GuideBook.ITEM.get()), "host holds the entry book");
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                } else {
                    press(mc, GLFW.GLFW_KEY_F9);
                }
                acted = true;
                return false;
            }
            if (!(mc.screen instanceof GuideScreen) || ticks < 12) {
                return false;
            }
            if (!fitted) {
                fit(mc);
                fitted = true;
            }
            if (host && !changed) {
                changed = click(mc, "role eyes", 0);
                return false;
            }
            return ticks > 28;
        }
        if (stage == 71) {
            if (!acted) {
                acted = click(mc, "interval " + (host ? 30 : 60), 0);
            }
            return acted && ticks > 20;
        }
        if (stage == 72) {
            if (host && !acted) {
                acted = click(mc, "invite DuoGuest", 2);
            } else if (!host && ticks > 20 && !acted) {
                mc.getConnection().sendUnsignedCommand("bemyeyes book");
                acted = true;
            }
            if (!host && acted && mc.screen instanceof GuideScreen screen) {
                return pages(screen).stream().anyMatch(page -> has(page, "/bemyeyes accept")) && ticks > 30;
            }
            return host && acted && mc.screen == null && ticks > 30;
        }
        if (stage == 73) {
            if (!host && !acted && mc.screen instanceof GuideScreen screen) {
                acted = click(mc, "accept", pages(screen).size() - 1);
            }
            if (state.active() && !state.travelling() && !state.suspended() && ticks > 15) {
                check(state.driver() != host, "inviter starts as eyes, invited player starts as driver");
                check(java.util.stream.IntStream.range(0, mc.player.getInventory().getContainerSize())
                        .noneMatch(slot -> mc.player.getInventory().getItem(slot).is(GuideBook.ITEM.get())),
                        "physical books are absent from both clients after starting");
                check(state.seconds() <= 30 && state.seconds() >= 25, "invitation snapshots the opening interval");
                shortcut(mc, GLFW.GLFW_KEY_F9, 1);
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    if (state.driver()) {
                        VisualSmoke.checkDriverFrame(mc, image);
                        Files.createDirectories(Path.of("smoke-screenshots"));
                        image.writeToFile(Path.of("smoke-screenshots", "guide-start-driver.png"));
                        double scale = image.getHeight() / (double) mc.getWindow().getGuiScaledHeight();
                        int pixels = 0;
                        for (int y = (int) (23 * scale); y < 34 * scale; y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                if ((image.getPixelRGBA(x, y) & 0xFFFFFF) != 0) {
                                    pixels++;
                                }
                            }
                        }
                        check(pixels > 50, "driver can see the shortcut without opening chat");
                    }
                }
                return true;
            }
            return false;
        }
        if (stage == 74 || stage == 78) {
            if (!acted) {
                press(mc, GLFW.GLFW_KEY_F9);
                acted = true;
            }
            if (mc.screen instanceof GuideScreen && state.suspended() && ticks > 15) {
                check(DuoClient.personalMenu(), "book is local to either role");
                check(!DuoClient.button(0, GLFW.GLFW_RELEASE, 0), "local book buttons remain usable");
                frame(mc, state.driver(), "guide-" + stage + "-" + (host ? "host" : "guest") + ".png");
                return true;
            }
            return false;
        }
        if (stage == 75) {
            if (host && !acted) {
                acted = click(mc, "interval 60", 0);
                return false;
            }
            if (ticks > 25 && state.seconds() == 60 && mc.screen instanceof GuideScreen screen
                    && selected(pages(screen).getFirst(), "/bemyeyes interval 60")) {
                return true;
            }
            return false;
        }
        if (stage == 76) {
            if (!host && !acted) {
                press(mc, GLFW.GLFW_KEY_F9);
                acted = true;
            }
            return ticks > 15 && !state.suspended() && mc.screen == null;
        }
        if (stage == 77) {
            if (host && state.driver() && !acted) {
                press(mc, GLFW.GLFW_KEY_E);
                acted = true;
            }
            return ticks > 15 && mc.screen instanceof InventoryScreen && !DuoClient.personalMenu();
        }
        if (stage == 79) {
            if (!host && !acted) {
                acted = click(mc, "swap", 0);
            }
            return ticks > 20 && state.driver() != host && mc.screen instanceof GuideScreen
                    && DuoClient.personalMenu() && state.suspended();
        }
        if (stage == 80) {
            if (!acted) {
                press(mc, GLFW.GLFW_KEY_F9);
                acted = true;
            }
            return ticks > 15 && !state.suspended() && mc.screen instanceof InventoryScreen;
        }
        if (stage == 81) {
            if (!acted) {
                if (state.driver()) {
                    press(mc, GLFW.GLFW_KEY_ESCAPE);
                }
                acted = true;
                return false;
            }
            if (mc.screen != null && !(mc.screen instanceof GuideScreen)) {
                return false;
            }
            if (!changed && ticks > 15) {
                bind(GLFW.GLFW_KEY_F10);
                press(mc, GLFW.GLFW_KEY_F9);
                check(mc.screen == null, "old key does not open the book after rebinding");
                press(mc, GLFW.GLFW_KEY_F10);
                changed = true;
            }
            return changed && ticks > 30 && mc.screen instanceof GuideScreen && DuoClient.personalMenu();
        }
        if (stage == 82) {
            if (!acted) {
                press(mc, GLFW.GLFW_KEY_F10);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1024, 768);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                acted = true;
            }
            if (ticks > 15 && mc.screen == null && !state.suspended()) {
                DuoClient.book(new Packets.Book(true, List.of(
                        Component.Serializer.toJson(Component.literal("late refresh"), mc.level.registryAccess()))));
                check(mc.screen == null, "late refresh does not reopen a closed book");
                System.out.println("BE_MY_EYES_GUIDE_SMOKE_PASS: " + (host ? "host" : "guest"));
                return true;
            }
        }
        if (stage == 83 || stage == 84) {
            if (!acted) {
                acted = click(mc, "interval add " + (stage == 83 ? 1 : -1), 1);
            }
            int expected = stage == 83 ? 62 : 60;
            if (acted && ticks > 20 && state.seconds() == expected && mc.screen instanceof GuideScreen screen
                    && interval(pages(screen).get(1)) == expected) {
                check(!screen.setPage(1), "custom adjustments preserve the current page");
                if (stage == 83) {
                    frame(mc, state.driver(), "guide-custom-" + (host ? "host" : "guest") + ".png");
                }
                return true;
            }
            return false;
        }
        if (stage == 85) {
            if (host && ticks <= 100 && mc.screen instanceof GuideScreen screen) {
                Style style = style(pages(screen).get(1), "/bemyeyes interval add 1");
                check(style != null, "custom increment remains available");
                for (int attempt = 0; attempt < 100; attempt++) {
                    screen.handleComponentClicked(style);
                }
            } else if (!host && !acted) {
                for (int attempt = 0; attempt < 200; attempt++) {
                    Packets.server(new Packets.BookAction(new GuideAction(GuideAction.Kind.ADJUST, 1, "")));
                    Packets.server(new Packets.OpenBook(true));
                }
                acted = true;
            }
            if (ticks > 140 && mc.screen instanceof GuideScreen screen && state.suspended()
                    && state.seconds() > 60 && state.seconds() <= 86
                    && interval(pages(screen).get(1)) == state.seconds()) {
                frame(mc, state.driver(), "guide-custom-" + (host ? "host" : "guest") + ".png");
                return true;
            }
            return false;
        }
        if (stage == 86) {
            if (host && !acted) {
                acted = click(mc, "interval 60", 0);
            }
            return ticks > 20 && state.seconds() == 60 && mc.screen instanceof GuideScreen screen
                    && interval(pages(screen).get(1)) == 60;
        }
        if (stage == 87) {
            if (!(mc.screen instanceof GuideScreen screen) || state.seconds() != 15
                    || interval(pages(screen).get(1)) != 15) {
                return false;
            }
            screen.setPage(1);
            check(!has(pages(screen).get(1), "/bemyeyes interval add -1"),
                    "decrease is disabled at the lower bound");
            if (!acted) {
                Packets.server(new Packets.BookAction(new GuideAction(GuideAction.Kind.ADJUST, -60, "")));
                acted = true;
            }
            return ticks > 20 && state.seconds() == 15;
        }
        if (stage == 88) {
            if (!acted) {
                acted = !host || click(mc, "interval add 60", 1);
            }
            if (acted && ticks > 20 && state.seconds() == 3600 && mc.screen instanceof GuideScreen screen
                    && interval(pages(screen).get(1)) == 3600) {
                check(!has(pages(screen).get(1), "/bemyeyes interval add 1"),
                        "increase is disabled at the upper bound");
                if (host) {
                    press(mc, GLFW.GLFW_KEY_F9);
                }
                return true;
            }
            return false;
        }
        return false;
    }

    static void server(ServerPlayer host, ServerPlayer guest, int stage) throws Exception {
        if (stage == 70) {
            check(!GuideBook.options(host).driver(), "opening role selection is stored on the correct player");
        } else if (stage == 71) {
            check(GuideBook.options(host).seconds() == 30 && GuideBook.options(guest).seconds() == 60,
                    "unpaired players have independent interval preferences");
        } else if (stage == 72) {
            command(host, "role driver");
            command(host, "interval 120");
            for (ServerPlayer player : new ServerPlayer[]{host, guest}) {
                player.getInventory().setItem(35, new ItemStack(GuideBook.ITEM.get()));
                player.getInventory().setItem(40, new ItemStack(GuideBook.ITEM.get()));
                player.containerMenu.setCarried(new ItemStack(GuideBook.ITEM.get()));
                player.inventoryMenu.getSlot(1).set(new ItemStack(GuideBook.ITEM.get()));
                player.inventoryMenu.getSlot(2).set(new ItemStack(Items.OAK_LOG, 3));
                player.inventoryMenu.sendAllDataToRemote();
            }
        } else if (stage == 73) {
            check(!clock(host).bodyControls() && clock(host).intervalSeconds() == 30,
                    "accepted invitation retains the offered role and interval");
            check(GuideBook.options(host).driver() && GuideBook.options(host).seconds() == 120,
                    "later preferences do not modify existing invitations");
            check(books(host) == 0 && books(guest) == 0 && GuideBook.played(host) && GuideBook.played(guest),
                    "start consumes inventory, offhand, carried and crafting-grid books");
            for (ServerPlayer player : new ServerPlayer[]{host, guest}) {
                check(player.getInventory().items.stream().filter(item -> item.is(Items.OAK_LOG))
                                .mapToInt(ItemStack::getCount).sum() == 3,
                        "starting returns ordinary crafting materials without guide books");
            }
        } else if (stage == 75) {
            check(clock(host).intervalSeconds() == 60 && clock(host).seconds() == 60,
                    "local settings keep the shared countdown paused");
        } else if (stage == 76) {
            command(host, "swap");
        } else if (stage == 79) {
            check(!clock(host).bodyControls(), "observer's book can swap roles");
        } else if (stage == 80) {
            check(host.containerMenu == host.inventoryMenu, "inventory menu survives the control book");
        } else if (stage == 82) {
            check(GuideBook.give(host) == 0 && GuideBook.give(guest) == 0,
                    "replacement books cannot be granted during play");
        } else if (stage == 83) {
            check(clock(host).intervalSeconds() == 62 && clock(host).seconds() == 62,
                    "simultaneous increments are applied cumulatively");
        } else if (stage == 84) {
            check(clock(host).intervalSeconds() == 60, "simultaneous decrements are applied cumulatively");
            hostSpam = spam(host);
            guestSpam = spam(guest);
        } else if (stage == 85) {
            check(clock(host).intervalSeconds() > 60 && clock(host).intervalSeconds() <= 86,
                    "client and server cooldowns bound thousands of repeated clicks");
            check(spam(host) <= hostSpam + 20 && spam(guest) <= guestSpam + 20,
                    "book actions do not use the native chat-command spam counter");
            // The remaining regression uses frequent native commands for acknowledgements.
            host.getServer().getPlayerList().setAllowCommandsForAllPlayers(true);
            System.out.println("BE_MY_EYES_GUIDE_RATE_PASS: server");
        } else if (stage == 86) {
            check(clock(host).intervalSeconds() == 60, "preset remains available after custom adjustments");
            command(host, "interval 15");
        } else if (stage == 87) {
            check(clock(host).intervalSeconds() == 15, "lower bound rejects out-of-range adjustment");
            command(host, "interval 3599");
        } else if (stage == 88) {
            check(clock(host).intervalSeconds() == 3600, "upper bound clamps a custom adjustment");
            command(host, "interval 60");
            System.out.println("BE_MY_EYES_GUIDE_CUSTOM_PASS: server");
        }
    }

    private static void command(ServerPlayer player, String command) {
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(0),
                "bemyeyes " + command);
    }

    private static SwapClock clock(ServerPlayer player) throws Exception {
        Field members = Class.forName("dev.duosight.server.Sessions").getDeclaredField("MEMBERS");
        members.setAccessible(true);
        Object session = ((Map<?, ?>) members.get(null)).get(player.getUUID());
        Field clock = session.getClass().getDeclaredField("clock");
        clock.setAccessible(true);
        return (SwapClock) clock.get(session);
    }

    private static int spam(ServerPlayer player) throws Exception {
        Field field = player.connection.getClass().getDeclaredField("chatSpamTickCount");
        field.setAccessible(true);
        return field.getInt(player.connection);
    }

    static void bind(int key) {
        GuideClient.OPEN.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
        KeyMapping.resetMapping();
    }

    static void shortcut(Minecraft mc, int key, int minimum) throws Exception {
        Field field = mc.gui.getChat().getClass().getDeclaredField("allMessages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<GuiMessage> messages = (List<GuiMessage>) field.get(mc.gui.getChat());
        var hints = messages.stream().map(GuiMessage::content).filter(component ->
                component.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("duosight.book.shortcut")).toList();
        check(hints.size() >= minimum, "opening shortcut is announced for each new session");
        String expected = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        check(hints.getFirst().getString().contains(expected), "shortcut hint follows the actual key binding");
    }

    private static List<Component> pages(GuideScreen screen) throws Exception {
        Field field = BookViewScreen.class.getDeclaredField("bookAccess");
        field.setAccessible(true);
        return ((BookViewScreen.BookAccess) field.get(screen)).pages();
    }

    private static boolean click(Minecraft mc, String command, int page) {
        if (!(mc.screen instanceof GuideScreen screen) || screen.setPage(page)) {
            return false;
        }
        int left = (screen.width - 192) / 2 + 36;
        for (int y = 32; y < 158; y++) {
            for (int x = left; x < left + 114; x++) {
                var style = screen.getClickedComponentStyleAt(x, y);
                if (style != null && style.getClickEvent() != null
                        && style.getClickEvent().getValue().equals("/bemyeyes " + command)) {
                    var mouse = (MouseHandlerAccess) mc.mouseHandler;
                    long window = mc.getWindow().getWindow();
                    mouse.duosight$move(window, x * mc.getWindow().getScreenWidth() / (double) screen.width,
                            y * mc.getWindow().getScreenHeight() / (double) screen.height);
                    mouse.duosight$press(window, 0, GLFW.GLFW_PRESS, 0);
                    mouse.duosight$press(window, 0, GLFW.GLFW_RELEASE, 0);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean has(Component page, String command) {
        ClickEvent click = page.getStyle().getClickEvent();
        return click != null && click.getValue().equals(command)
                || page.getSiblings().stream().anyMatch(child -> has(child, command));
    }

    private static boolean selected(Component page, String command) {
        ClickEvent click = page.getStyle().getClickEvent();
        return click != null && click.getValue().equals(command) && page.getStyle().isBold()
                || page.getSiblings().stream().anyMatch(child -> selected(child, command));
    }

    private static int interval(Component page) {
        if (page.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("duosight.book.every")) {
            return ((Number) contents.getArgs()[0]).intValue();
        }
        return page.getSiblings().stream().mapToInt(GuideSmoke::interval).filter(value -> value != -1)
                .findFirst().orElse(-1);
    }

    private static Style style(Component page, String command) {
        ClickEvent click = page.getStyle().getClickEvent();
        if (click != null && click.getValue().equals(command)) {
            return page.getStyle();
        }
        return page.getSiblings().stream().map(child -> style(child, command))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static void fit(Minecraft mc) {
        Language original = Language.getInstance();
        try {
            for (String code : List.of("en_us", "zh_cn", "zh_tw", "zh_hk", "ja_jp", "fr_fr", "de_de")) {
                Language.inject(ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us", code), false));
                for (int seconds : new int[]{15, 120, 3600}) {
                    for (boolean driver : new boolean[]{true, false}) {
                        for (var pages : List.of(
                                GuidePages.create(new PairingOptions(driver, seconds), null,
                                        new GuidePages.Invitation("WWWWWWWWWWWWWWWW", driver, seconds),
                                        List.of("WWWWWWWWWWWWWWWW", "DuoHost", "DuoGuest")),
                                GuidePages.create(new PairingOptions(driver, seconds),
                                        new GuidePages.Shared("WWWWWWWWWWWWWWWW", driver, seconds, seconds),
                                        null, List.of()))) {
                            for (Component page : pages) {
                                int rows = mc.font.split(page, 114).size();
                                check(rows <= 14, "page text fits in " + code + ": " + rows + " rows");
                            }
                        }
                    }
                }
                System.out.println("BE_MY_EYES_GUIDE_LAYOUT_PASS: " + code);
            }
        } finally {
            Language.inject(original);
        }
    }

    private static void frame(Minecraft mc, boolean driver, String file) throws Exception {
        Files.createDirectories(Path.of("smoke-screenshots"));
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(Path.of("smoke-screenshots", file));
            double scale = image.getWidth() / (double) mc.getWindow().getGuiScaledWidth();
            int left = (mc.getWindow().getGuiScaledWidth() - 192) / 2;
            int pagePixels = 0;
            for (int y = (int) (36 * scale); y < 145 * scale; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int color = image.getPixelRGBA(x, y);
                    if (x > (left + 36) * scale && x < (left + 150) * scale && (color & 255) > 80) {
                        pagePixels++;
                    }
                    if (driver && x < (left - 5) * scale) {
                        check((color & 255) <= 20 && ((color >> 8) & 255) <= 20
                                && ((color >> 16) & 255) <= 20, "driver's world stays hidden behind the book");
                    }
                }
            }
            check(pagePixels > 100, "both roles can see the rendered book");
        }
    }

    private static void press(Minecraft mc, int key) {
        var keyboard = (KeyboardHandlerAccess) mc.keyboardHandler;
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0);
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new AssertionError("Be My Eyes control-book test: " + message);
        }
    }

    private GuideSmoke() {}
}
