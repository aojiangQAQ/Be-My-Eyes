package dev.duosight.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.duosight.client.DuoClient;
import dev.duosight.client.RecipeView;
import dev.duosight.mixin.KeyboardHandlerAccess;
import dev.duosight.mixin.MouseHandlerAccess;
import dev.duosight.net.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = "duosight_smoke", value = Dist.CLIENT)
public final class ChatSmoke {
    private static final Set<String> RECEIVED = new HashSet<>();
    private static boolean acted, typed;
    private static int wait;

    @SubscribeEvent
    public static void received(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        for (String marker : new String[]{"BME_CHAT_host", "BME_CHAT_guest", "BME_CMD_host",
                "BME_CMD_guest", "BME_CONTAINER_CHAT_guest"}) {
            if (message.contains(marker)) {
                RECEIVED.add(marker);
                check(message.contains(marker.endsWith("guest") ? "DuoGuest" : "DuoHost"),
                        "messages retain the sender's identity: " + marker);
            }
        }
    }

    static void reset() {
        acted = false;
        typed = false;
        wait = 0;
    }

    static boolean client(Minecraft mc, Packets.State state, int stage, int ticks, boolean host) throws Exception {
        String role = host ? "host" : "guest";
        if (ticks % 100 == 0) {
            System.out.println("BE_MY_EYES_CHAT_WAIT: " + role + " stage " + stage + " screen "
                    + (mc.screen == null ? "world" : mc.screen.getClass().getSimpleName())
                    + " suspended " + state.suspended() + " personal " + DuoClient.personalMenu());
        }
        if (stage == 50 || stage == 53 || stage == 57) {
            if (stage == 57 && !acted && !(mc.screen instanceof InventoryScreen)) {
                if (state.driver() && mc.screen == null && !typed) {
                    press(mc, GLFW.GLFW_KEY_E);
                    typed = true;
                }
                return false;
            }
            if (!acted) {
                mc.options.chatHeightFocused().set(0.2);
                mc.options.chatWidth().set(0.6);
                if (stage == 53) {
                    mc.options.keyChat.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Y));
                    KeyMapping.resetMapping();
                }
                open(mc, stage == 53 ? GLFW.GLFW_KEY_Y : GLFW.GLFW_KEY_T,
                        stage == 53 ? 'y' : 't', "");
                input(mc).setValue("BME_DRAFT_" + role);
                if (stage == 53) {
                    mc.options.keyChat.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_T));
                    KeyMapping.resetMapping();
                }
                acted = true;
            }
            if (state.suspended() && ticks > 12) {
                check(DuoClient.personalMenu(), "both players can have local chat open");
                check(!DuoClient.button(0, GLFW.GLFW_RELEASE, 0), "chat mouse remains enabled");
                frame(mc, state.driver(), "chat-" + stage + "-" + role + ".png");
                return true;
            }
            return false;
        }
        if (stage == 51 || stage == 52) {
            if (!acted) {
                if (stage == 52) {
                    open(mc, GLFW.GLFW_KEY_SLASH, '/', "/");
                    mc.screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
                    check(input(mc).getValue().equals("BME_CHAT_" + role), "native per-player chat history");
                    input(mc).setValue("/bemyeyes interv");
                    mc.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                    check(input(mc).getValue().contains("interval"), "native command completion");
                }
                submit(mc, stage == 51 ? "BME_CHAT_" + role : "/me BME_CMD_" + role);
                acted = true;
            }
            String prefix = stage == 51 ? "BME_CHAT_" : "BME_CMD_";
            return RECEIVED.contains(prefix + "host") && RECEIVED.contains(prefix + "guest")
                    && !state.suspended() && ticks > 12;
        }
        if (stage == 54) {
            if (host && !acted) {
                mc.screen.onClose();
                acted = true;
            }
            if (ticks > 12 && state.suspended()) {
                check(host ? mc.screen == null : mc.screen instanceof ChatScreen,
                        "closing one chat does not close the partner's chat");
                if (host) {
                    check(DuoClient.key(GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0),
                            "shared body input remains paused while partner chats");
                }
                return true;
            }
            return false;
        }
        if (stage == 55) {
            if (host && !acted) {
                open(mc, GLFW.GLFW_KEY_SLASH, '/', "/");
                input(mc).setValue("/bemyeyes status");
                acted = true;
            }
            if (ticks > 12 && state.suspended()) {
                check(mc.screen instanceof ChatScreen, "chat available while partner is in a menu");
                check(input(mc).getValue().equals(host ? "/bemyeyes status" : "BME_DRAFT_guest"),
                        "local typing is not forwarded into the partner's chat");
                frame(mc, state.driver(), "chat-suspended-" + role + ".png");
                return true;
            }
            return false;
        }
        if (stage == 56 || stage == 58) {
            if (!acted) {
                mc.screen.onClose();
                acted = true;
            }
            if (ticks > 12 && !state.suspended()) {
                check(stage == 56 ? mc.screen == null : mc.screen instanceof InventoryScreen,
                        "closing chat restores the previous world or inventory screen");
                return true;
            }
            return false;
        }
        if (stage == 59 || stage == 60) {
            if (!(mc.screen instanceof InventoryScreen screen) || state.suspended()) {
                return false;
            }
            var book = screen.getRecipeBookComponent();
            if (!book.isVisible()) {
                if (state.driver() && !acted) {
                    ImageButton button = (ImageButton) screen.children().stream()
                            .filter(ImageButton.class::isInstance).findFirst().orElseThrow();
                    mouse(mc, button.getX() + 5, button.getY() + 5);
                    acted = true;
                }
                return false;
            }
            EditBox search = search(book);
            if (stage == 59 && state.driver() && !search.isFocused()) {
                mouse(mc, search.getX() + 5, search.getY() + 5);
                return false;
            }
            if (!((RecipeView) book).duosight$editing()) {
                return false;
            }
            String expected = stage == 59 ? "t/" : "t/t/";
            if (state.driver() && !typed) {
                typeKey(mc, GLFW.GLFW_KEY_T, 't');
                typeKey(mc, GLFW.GLFW_KEY_SLASH, '/');
                check(mc.screen == screen, "recipe search input does not open chat");
                typed = true;
            }
            if (search.getValue().equals(expected) && ++wait > 12) {
                return true;
            }
            return false;
        }
        if (stage == 61) {
            if (!host && !acted) {
                open(mc, GLFW.GLFW_KEY_T, 't', "");
                input(mc).setValue("BME_CONTAINER_CHAT_guest");
                acted = true;
            }
            if (ticks > 12 && state.suspended()) {
                check(!host ? mc.screen instanceof ChatScreen : mc.screen instanceof InventoryScreen,
                        "observer can chat despite the body's focused recipe search box");
                if (!host) {
                    frame(mc, false, "chat-observer-recipe.png");
                }
                return true;
            }
            return false;
        }
        if (stage == 62) {
            if (!host && !acted) {
                submit(mc, "BME_CONTAINER_CHAT_guest");
                acted = true;
            }
            if (ticks > 12 && !state.suspended() && RECEIVED.contains("BME_CONTAINER_CHAT_guest")
                    && mc.screen instanceof InventoryScreen screen) {
                check(search(screen.getRecipeBookComponent()).getValue().equals("t/t/"),
                        "recipe search survives chat and a role swap");
                return true;
            }
            return false;
        }
        if (stage == 63) {
            if (state.driver() && !acted && mc.screen != null) {
                press(mc, GLFW.GLFW_KEY_ESCAPE);
                acted = true;
            }
            if (state.driver() && ticks > 8 && mc.screen != null) {
                press(mc, GLFW.GLFW_KEY_ESCAPE);
            }
            return ticks > 12 && mc.screen == null && !state.suspended();
        }
        if (stage == 64 && ticks > 12 && mc.screen == null && !state.suspended()) {
            if (state.driver()) {
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    VisualSmoke.checkDriverFrame(mc, image);
                }
            }
            System.out.println("BE_MY_EYES_CHAT_SMOKE_PASS: " + role);
            return true;
        }
        return false;
    }

    private static void open(Minecraft mc, int key, char character, String initial) throws Exception {
        typeKey(mc, key, character);
        check(mc.screen instanceof ChatScreen, "native chat opens for either role");
        check(input(mc).getValue().equals(initial), "opening character is not duplicated");
    }

    private static void submit(Minecraft mc, String message) throws Exception {
        input(mc).setValue(message);
        mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        if (mc.screen instanceof ChatScreen) {
            mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
        check(!(mc.screen instanceof ChatScreen), "native chat submits and closes");
    }

    private static EditBox input(Minecraft mc) throws Exception {
        Field field = ChatScreen.class.getDeclaredField("input");
        field.setAccessible(true);
        return (EditBox) field.get(mc.screen);
    }

    private static EditBox search(RecipeBookComponent book) throws Exception {
        Field field = RecipeBookComponent.class.getDeclaredField("searchBox");
        field.setAccessible(true);
        return (EditBox) field.get(book);
    }

    private static void press(Minecraft mc, int key) {
        var keyboard = (KeyboardHandlerAccess) mc.keyboardHandler;
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0);
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void typeKey(Minecraft mc, int key, char character) {
        var keyboard = (KeyboardHandlerAccess) mc.keyboardHandler;
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0);
        keyboard.duosight$character(mc.getWindow().getWindow(), character, 0);
        keyboard.duosight$key(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void mouse(Minecraft mc, double x, double y) {
        var mouse = (MouseHandlerAccess) mc.mouseHandler;
        long window = mc.getWindow().getWindow();
        mouse.duosight$move(window, x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
        mouse.duosight$press(window, 0, GLFW.GLFW_PRESS, 0);
        mouse.duosight$press(window, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void frame(Minecraft mc, boolean driver, String name) throws Exception {
        Files.createDirectories(Path.of("smoke-screenshots"));
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(Path.of("smoke-screenshots", name));
            double scale = image.getHeight() / (double) mc.getWindow().getGuiScaledHeight();
            int backdropPixels = 0, inputPixels = 0, chatPixels = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getPixelRGBA(x, y) & 0xFFFFFF;
                    if (y >= 24 * scale && y < 40 * scale) {
                        if (driver) {
                            check(rgb == 0, "driver cannot see the world while chatting");
                        } else if (rgb != 0) {
                            backdropPixels++;
                        }
                    }
                    if (rgb != 0 && y >= image.getHeight() - 14 * scale) {
                        inputPixels++;
                    }
                    if (rgb != 0 && y > image.getHeight() - 80 * scale
                            && y < image.getHeight() - 20 * scale) {
                        chatPixels++;
                    }
                }
            }
            check(driver || backdropPixels > 100, "observer retains the body view while chatting");
            check(inputPixels > 10, "native chat input is visible");
            check(chatPixels > 20, "native chat history is visible");
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new AssertionError("Be My Eyes chat test: " + message);
        }
    }

    private ChatSmoke() {}
}
