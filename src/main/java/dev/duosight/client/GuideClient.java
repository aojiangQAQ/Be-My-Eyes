package dev.duosight.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.duosight.DuoSight;
import dev.duosight.core.ActionCooldown;
import dev.duosight.core.GuideAction;
import dev.duosight.net.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DuoSight.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GuideClient {
    public static final KeyMapping OPEN = new KeyMapping("key.duosight.guide",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.duosight");
    private static final ActionCooldown OPEN_COOLDOWN = new ActionCooldown(500_000_000L);
    private static final ActionCooldown ACTION_COOLDOWN = new ActionCooldown(300_000_000L);
    private static boolean refreshPending;

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    public static boolean key(int key, int scan, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (!OPEN.matches(key, scan) || mc.player == null || mc.getConnection() == null
                || mc.screen instanceof ReceivingLevelScreen || mc.screen instanceof WinScreen) {
            return false;
        }
        if (mc.screen instanceof KeyBindsScreen) {
            return false;
        }
        if (action == GLFW.GLFW_PRESS) {
            if (mc.screen instanceof GuideScreen) {
                mc.screen.onClose();
            } else {
                open();
            }
        }
        return true;
    }

    public static void open() {
        if (OPEN_COOLDOWN.allow(System.nanoTime())) {
            Packets.server(new Packets.OpenBook(false));
        }
    }

    public static boolean action(GuideAction action) {
        if (action == null || !action.valid() || !ACTION_COOLDOWN.allow(System.nanoTime())) {
            return false;
        }
        Packets.server(new Packets.BookAction(action));
        return true;
    }

    public static void refresh() {
        refreshPending = true;
    }

    public static void received() {
        refreshPending = false;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GuideScreen) || mc.getConnection() == null) {
            refreshPending = false;
        } else if (refreshPending && OPEN_COOLDOWN.allow(System.nanoTime())) {
            refreshPending = false;
            Packets.server(new Packets.OpenBook(true));
        }
    }

    public static void reset() {
        refreshPending = false;
        OPEN_COOLDOWN.reset();
        ACTION_COOLDOWN.reset();
    }

    private GuideClient() {}
}
