package dev.duosight.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import dev.duosight.client.DuoClient;
import dev.duosight.client.RecipeView;
import dev.duosight.mixin.KeyboardHandlerAccess;
import dev.duosight.mixin.MouseHandlerAccess;
import dev.duosight.mixin.RecipePageAccess;
import dev.duosight.net.Packets;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod("duosight_smoke")
@Mod.EventBusSubscriber(modid = "duosight_smoke", value = Dist.CLIENT)
public final class VisualSmoke {
    private static int phase, ticks;
    private static CameraType camera;
    private static boolean pause;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent.Post event) throws Exception {
        if (System.getProperty("duosight.networkSmoke") != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (phase == 0) {
            if (mc.screen == null || mc.getOverlay() != null || !mc.isGameLoadFinished()) {
                return;
            }
            check(mc.keyboardHandler instanceof KeyboardHandlerAccess, "keyboard mixin");
            check(mc.mouseHandler instanceof MouseHandlerAccess, "mouse mixin");
            check(new RecipeBookComponent() instanceof RecipeView, "recipe book mixin");
            check(new RecipeBookPage() instanceof RecipePageAccess, "recipe page mixin");
            camera = mc.options.getCameraType();
            pause = mc.options.pauseOnLostFocus;
            DuoClient.state(new Packets.State(true, true, true, -1, 120, 0, false,
                    "minecraft:overworld", false, false));
            phase = 1;
            ticks = 0;
        } else if (++ticks >= 10 && phase == 1) {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                save(image, "driver-black.png");
                checkDriverFrame(mc, image);
                System.out.println("DUOSIGHT_SMOKE: black frame with countdown "
                        + image.getWidth() + "x" + image.getHeight());
            }
            mc.setScreen(new PauseScreen(true) {
                @Override
                protected void init() {
                    addRenderableWidget(Button.builder(Component.literal("Return"), button -> onClose())
                            .bounds(width / 2 - 80, height / 2, 160, 20).build());
                }
            });
            check(DuoClient.active(), "menu preserves pairing");
            check(DuoClient.personalMenu(), "personal pause menu");
            check(!DuoClient.key(GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0), "menu keyboard input");
            check(!DuoClient.move(100, 100), "menu mouse input");
            phase = 2;
            ticks = 0;
        } else if (ticks >= 10 && phase == 2) {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                save(image, "driver-menu.png");
                int visible = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getPixelRGBA(x, y) & 0x00FFFFFF) != 0) {
                            visible++;
                        }
                    }
                }
                check(visible > 100, "menu visible over black frame");
            }
            mc.setScreen(null);
            check(!DuoClient.personalMenu(), "return from personal menu");
            check(DuoClient.active(), "return preserves pairing");
            DuoClient.state(new Packets.State(true, true, false, -1, 120, 1, false,
                    "minecraft:overworld", false, false));
            check(DuoClient.key(GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0), "observer keyboard lock");
            check(DuoClient.move(100, 100), "observer camera lock");
            check(DuoClient.button(0, GLFW.GLFW_PRESS, 0), "observer mouse lock");
            check(mc.options.getCameraType() == CameraType.FIRST_PERSON, "first person lock");
            phase = 3;
            ticks = 0;
        } else if (ticks >= 10 && phase == 3) {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int visible = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getPixelRGBA(x, y) & 0x00FFFFFF) != 0) {
                            visible++;
                        }
                    }
                }
                check(visible > image.getWidth() * image.getHeight() / 2, "observer visibility");
                save(image, "observer-visible.png");
            }
            DuoClient.state(new Packets.State(false, false, false, -1, 0, 0, false,
                    "", false, false));
            check(mc.options.getCameraType() == camera, "camera restoration");
            check(mc.options.pauseOnLostFocus == pause, "pause restoration");
            check(!DuoClient.key(GLFW.GLFW_KEY_W, 0, GLFW.GLFW_PRESS, 0), "input restoration");
            phase = 4;
            System.out.println("DUOSIGHT_VISUAL_SMOKE_PASS");
            mc.stop();
        }
    }

    private static void save(NativeImage image, String name) throws Exception {
        Path directory = Path.of("smoke-screenshots");
        Files.createDirectories(directory);
        image.writeToFile(directory.resolve(name));
    }

    static void checkDriverFrame(Minecraft mc, NativeImage image) throws Exception {
        double scale = image.getHeight() / (double) mc.getWindow().getGuiScaledHeight();
        var field = DuoClient.class.getDeclaredField("guideHintTicks");
        field.setAccessible(true);
        boolean hint = field.getInt(null) > 0 && mc.screen == null;
        int hintWidth = mc.font.width(Component.translatable("duosight.book.shortcut_hint",
                Component.keybind("key.duosight.guide")));
        int visible = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getPixelRGBA(x, y) & 0xFFFFFF;
                if (y >= 8 * scale && y < 19 * scale
                        && Math.abs(x - image.getWidth() / 2.0) <= 120 * scale) {
                    if (rgb != 0) {
                        visible++;
                    }
                } else if (!(hint && y >= 23 * scale && y < 34 * scale
                        && Math.abs(x - image.getWidth() / 2.0) <= (hintWidth / 2.0 + 2) * scale)) {
                    check(rgb == 0, "world hidden outside countdown at " + x + "," + y);
                }
            }
        }
        check(visible > 50, "driver countdown visible");
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Be My Eyes visual test failed: " + name);
        }
    }
}
