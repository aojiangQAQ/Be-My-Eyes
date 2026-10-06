package dev.duosight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.duosight.DuoSight;
import dev.duosight.mixin.KeyboardHandlerAccess;
import dev.duosight.mixin.MouseHandlerAccess;
import dev.duosight.mixin.MenuAccess;
import dev.duosight.mixin.HorseMenuAccess;
import dev.duosight.net.Packets;
import dev.duosight.server.GuideBook;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = DuoSight.ID, value = Dist.CLIENT)
public final class DuoClient {
    private static Packets.State state = inactive();
    private static CameraType previousCamera = CameraType.FIRST_PERSON;
    private static boolean previousPause;
    private static boolean injecting;
    private static final Set<Integer> REMOTE_KEYS = new HashSet<>();
    private static ItemStack[] previousInventory;
    private static RecipeBook previousRecipes;
    private static int previousLevel, previousTotalExperience;
    private static float previousExperienceProgress;
    private static CompoundTag lastView;
    private static boolean remoteMenu;
    private static int menuId = -1;
    private static String menuKind = "";
    private static float health = 20;
    private static int food = 20, level;
    private static boolean mouseKnown;
    private static double mouseX, mouseY;
    private static double lookX, lookY;
    private static double cursorX = 0.5, cursorY = 0.5;
    private static boolean cursorDirty;
    private static boolean personalMenu;
    private static Screen savedScreen;
    private static CompoundTag savedContainerView;
    private static boolean restoringScreen;
    private static boolean resumeScreen;
    private static Packets.View latestView;
    private static Packets.Pose latestPose;
    private static long poseReceived, lastMouseSend, lastPoseSend;
    private static int poseSequence, readyEpoch = -1;
    private static int lastTimer = -1;
    private static int guideHintTicks;
    private static Packets.Cursor lastCursor;
    private static Packets.Cursor latestCursor;
    private static final long FRAME_INTERVAL = 16_666_667L;

    private static Packets.State inactive() {
        return new Packets.State(false, false, false, -1, 0, 0, false, "", false, false);
    }

    public static boolean active() {
        return state.active();
    }

    public static boolean isBody() {
        return state.active() && state.body();
    }

    public static boolean restoringScreen() {
        return restoringScreen;
    }

    public static boolean driver() {
        return state.active() && state.driver();
    }

    public static boolean remoteBody() {
        return state.active() && state.body() && !state.driver();
    }

    public static boolean remoteKey(int key) {
        return REMOTE_KEYS.contains(key);
    }

    public static boolean personalMenu() {
        return active() && personalMenu;
    }

    private static boolean blocked() {
        return personalMenu || state.suspended() || !loaded();
    }

    private static boolean loaded() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null
                && state.dimension().equals(mc.level.dimension().location().toString())
                && !(mc.screen instanceof ReceivingLevelScreen) && !(mc.screen instanceof WinScreen)
                && (state.body() || mc.level.getEntity(state.bodyId()) != null);
    }

    public static void screenChange(Screen next) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if ((next instanceof PauseScreen || next instanceof ChatScreen || next instanceof GuideScreen)
                && !personalMenu) {
            savedScreen = mc.screen instanceof AbstractContainerScreen<?> ? mc.screen : null;
            savedContainerView = savedScreen instanceof ContainerView container ? container.duosight$capture() : null;
            if (state.body() && savedScreen != null && loaded()) {
                lastView = snapshot(mc);
                Packets.server(new Packets.View(state.epoch(), lastView));
            }
            personalMenu = true;
            resetInput();
            reportMenu(true);
        } else if (personalMenu && (next == null || next instanceof ReceivingLevelScreen
                || next instanceof WinScreen)) {
            personalMenu = false;
            resumeScreen = next == null;
            if (!resumeScreen) {
                savedScreen = null;
                savedContainerView = null;
            }
            resetInput();
            reportMenu(false);
        }
    }

    private static void reportMenu(boolean open) {
        if (Minecraft.getInstance().getConnection() != null) {
            Packets.server(new Packets.Menu(open));
        }
    }

    public static void state(Packets.State next) {
        Minecraft mc = Minecraft.getInstance();
        boolean beginning = next.active() && !state.active();
        if (beginning) {
            guideHintTicks = 200;
            mc.gui.getChat().addMessage(Component.translatable("duosight.book.shortcut",
                    Component.keybind("key.duosight.guide")));
            previousCamera = mc.options.getCameraType();
            previousPause = mc.options.pauseOnLostFocus;
            if (!next.body() && mc.player != null) {
                previousLevel = mc.player.experienceLevel;
                previousTotalExperience = mc.player.totalExperience;
                previousExperienceProgress = mc.player.experienceProgress;
                previousRecipes = new RecipeBook();
                previousRecipes.copyOverData(mc.player.getRecipeBook());
                previousInventory = new ItemStack[mc.player.getInventory().getContainerSize()];
                for (int i = 0; i < previousInventory.length; i++) {
                    ItemStack item = mc.player.getInventory().getItem(i);
                    previousInventory[i] = item.is(GuideBook.ITEM.get()) ? ItemStack.EMPTY : item.copy();
                }
            }
        }
        boolean transition = state.active() != next.active() || state.driver() != next.driver()
                || state.epoch() != next.epoch() || state.suspended() != next.suspended();
        boolean travel = next.travelling() && (!state.travelling() || state.epoch() != next.epoch());
        boolean refreshBook = !next.travelling() && mc.screen instanceof GuideScreen
                && (state.active() != next.active() || state.travelling() != next.travelling()
                || state.driver() != next.driver() || state.seconds() != next.seconds()
                || state.epoch() != next.epoch());
        state = next;
        if (transition) {
            resetInput();
            lastView = null;
            latestPose = null;
            lastCursor = null;
            latestCursor = null;
            poseSequence = 0;
            lastMouseSend = lastPoseSend = 0;
        }
        if (travel) {
            readyEpoch = -1;
            latestView = null;
            savedScreen = null;
            savedContainerView = null;
            menuId = -1;
            menuKind = "";
            if (remoteMenu && !personalMenu && mc.screen instanceof AbstractContainerScreen<?>) {
                mc.player.containerMenu = mc.player.inventoryMenu;
                mc.setScreen(null);
            }
            remoteMenu = false;
        }
        if (beginning && (mc.screen instanceof ChatScreen || mc.screen instanceof PauseScreen
                || mc.screen instanceof GuideScreen)) {
            screenChange(mc.screen);
        }
        if (next.active()) {
            mc.options.pauseOnLostFocus = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.screen == null) {
                mc.mouseHandler.grabMouse();
            }
            if (next.changed() && mc.player != null) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65f,
                        next.driver() ? 0.65f : 1.4f);
            } else if (!next.suspended() && next.seconds() != lastTimer
                    && next.seconds() <= 10 && next.seconds() > 0 && mc.player != null) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3f, 1.8f);
            }
        } else {
            clear(mc);
        }
        lastTimer = next.seconds();
        if (refreshBook && mc.getConnection() != null) {
            GuideClient.refresh();
        }
    }

    private static void resetInput() {
        Minecraft mc = Minecraft.getInstance();
        REMOTE_KEYS.clear();
        KeyMapping.releaseAll();
        lookX = lookY = 0;
        cursorDirty = false;
        mouseKnown = false;
        MouseHandlerAccess mouse = (MouseHandlerAccess) mc.mouseHandler;
        mouse.duosight$left(false);
        mouse.duosight$middle(false);
        mouse.duosight$right(false);
        mouse.duosight$activeButton(-1);
        mouse.duosight$clickDepth(0);
        mouse.duosight$pressedTime(0);
        if (mc.screen != null) {
            mc.screen.setDragging(false);
            if (mc.screen instanceof ContainerInput container) {
                container.duosight$resetInput();
            }
        }
        mouse.duosight$dx(0);
        mouse.duosight$dy(0);
    }

    private static void clear(Minecraft mc) {
        mc.options.setCameraType(previousCamera);
        mc.options.pauseOnLostFocus = previousPause;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            if (previousInventory != null) {
                mc.player.experienceLevel = previousLevel;
                mc.player.totalExperience = previousTotalExperience;
                mc.player.experienceProgress = previousExperienceProgress;
                for (int i = 0; i < previousInventory.length; i++) {
                    mc.player.getInventory().setItem(i, previousInventory[i]);
                }
            }
            if (previousRecipes != null) {
                mc.player.getRecipeBook().copyOverData(previousRecipes);
            }
            if (remoteMenu) {
                mc.player.containerMenu = mc.player.inventoryMenu;
                if (!(mc.screen instanceof ChatScreen) && !(mc.screen instanceof GuideScreen)) {
                    mc.setScreen(null);
                }
            }
        }
        previousInventory = null;
        previousRecipes = null;
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR,
                mc.screen == null ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        remoteMenu = false;
        menuId = -1;
        menuKind = "";
        lastView = null;
        personalMenu = resumeScreen = false;
        guideHintTicks = 0;
        savedScreen = null;
        savedContainerView = null;
        latestView = null;
        latestPose = null;
        lastCursor = null;
        latestCursor = null;
        readyEpoch = -1;
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        GuideClient.reset();
        if (active()) {
            state(inactive());
        }
    }

    @SubscribeEvent
    public static void clonePlayer(ClientPlayerNetworkEvent.Clone event) {
        if (!active()) {
            return;
        }
        resetInput();
        lastView = null;
        latestView = null;
        latestPose = null;
        lastCursor = null;
        latestCursor = null;
        readyEpoch = -1;
        menuId = -1;
        menuKind = "";
        remoteMenu = false;
        savedScreen = null;
        savedContainerView = null;
    }

    public static boolean key(int key, int scan, int action, int modifiers) {
        if (injecting) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if ((key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25 || !editingText(mc.screen))
                && GuideClient.key(key, scan, action)) {
            return true;
        }
        if (!active()) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_F8) {
            if (action == GLFW.GLFW_PRESS) {
                Packets.server(new Packets.Stop());
            }
            return true;
        }
        if (mc.screen instanceof DuoChatScreen screen && action == GLFW.GLFW_RELEASE) {
            screen.openingKeyReleased(key);
        }
        boolean chatKey = mc.options.keyChat.matches(key, scan);
        boolean commandKey = mc.options.keyCommand.matches(key, scan);
        if ((chatKey || commandKey) && !(mc.screen instanceof ChatScreen)
                && loaded() && (!(driver() || personalMenu) || !editingText(mc.screen))) {
            if (action == GLFW.GLFW_PRESS) {
                var status = mc.getChatStatus();
                if (status.isChatAllowed(mc.isLocalServer())) {
                    mc.setScreen(new DuoChatScreen(commandKey ? "/" : "", key));
                } else {
                    mc.gui.setOverlayMessage(status.getMessage(), false);
                }
            }
            return true;
        }
        if (personalMenu) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE
                && (mc.screen == null || !driver())) {
            if (action == GLFW.GLFW_PRESS && !(mc.screen instanceof ReceivingLevelScreen)
                    && !(mc.screen instanceof WinScreen)) {
                mc.setScreen(new PauseScreen(true));
            }
            return true;
        }
        if (blocked()) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_F1 || key == GLFW.GLFW_KEY_F3 || key == GLFW.GLFW_KEY_F4
                || key == GLFW.GLFW_KEY_F5 || key == GLFW.GLFW_KEY_F11) {
            return true;
        }
        if (!driver()) {
            return true;
        }
        if (!state.body()) {
            send(Packets.Input.KEY, key, scan, action, modifiers, 0, 0);
            return true;
        }
        return false;
    }

    private static boolean editingText(GuiEventListener listener) {
        if (listener instanceof EditBox box && box.isVisible() && box.canConsumeInput()) {
            return true;
        }
        if (listener instanceof RecipeUpdateListener recipes
                && ((RecipeView) recipes.getRecipeBookComponent()).duosight$editing()) {
            return true;
        }
        return listener instanceof ContainerEventHandler container
                && container.children().stream().anyMatch(DuoClient::editingText);
    }

    public static boolean character(int code, int modifiers) {
        if (!injecting && active() && Minecraft.getInstance().screen instanceof DuoChatScreen screen
                && screen.openingCharacter()) {
            return true;
        }
        if (injecting || !active() || personalMenu) {
            return false;
        }
        if (blocked()) {
            return true;
        }
        if (driver() && !state.body()) {
            send(Packets.Input.CHARACTER, code, 0, 0, modifiers, 0, 0);
        }
        return !state.body() || !driver();
    }

    public static boolean button(int button, int action, int modifiers) {
        if (injecting || !active() || personalMenu) {
            return false;
        }
        if (blocked()) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mc.screen == null && mc.player != null
                && (mc.player.getMainHandItem().is(GuideBook.ITEM.get())
                || mc.player.getOffhandItem().is(GuideBook.ITEM.get()))) {
            if (action == GLFW.GLFW_PRESS) {
                GuideClient.open();
            }
            return true;
        }
        if (driver() && !state.body()) {
            flushMouse();
            send(Packets.Input.BUTTON, button, 0, action, modifiers, 0, 0);
        }
        return !state.body() || !driver();
    }

    public static boolean scroll(double x, double y) {
        if (injecting || !active() || personalMenu) {
            return false;
        }
        if (blocked()) {
            return true;
        }
        if (driver() && !state.body()) {
            flushMouse();
            send(Packets.Input.SCROLL, 0, 0, 0, 0, x, y);
        }
        return !state.body() || !driver();
    }

    public static boolean move(double x, double y) {
        if (injecting || !active() || personalMenu) {
            return false;
        }
        if (blocked()) {
            return true;
        }
        if (!state.body() && driver()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                cursorX = x * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth()
                        - mc.screen.width / 2.0;
                cursorY = y * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight()
                        - mc.screen.height / 2.0;
                cursorDirty = true;
            } else if (mouseKnown) {
                double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
                double scale = sensitivity * sensitivity * sensitivity * 8.0;
                lookX += (x - mouseX) * scale;
                lookY += (y - mouseY) * scale * (mc.options.invertYMouse().get() ? -1 : 1);
            }
            mouseKnown = true;
            mouseX = x;
            mouseY = y;
        }
        return !state.body() || !driver();
    }

    private static void send(int kind, int code, int scan, int action, int modifiers, double x, double y) {
        Packets.server(new Packets.Input(state.epoch(), kind, code, scan, action, modifiers, x, y));
    }

    private static void flushMouse() {
        if (lookX != 0 || lookY != 0) {
            send(Packets.Input.LOOK, 0, 0, 0, 0,
                    Math.max(-10000, Math.min(10000, lookX)), Math.max(-10000, Math.min(10000, lookY)));
            lookX = lookY = 0;
        }
        if (cursorDirty) {
            send(Packets.Input.CURSOR, 0, 0, 0, 0, cursorX, cursorY);
            cursorDirty = false;
        }
    }

    public static void remoteInput(Packets.Input input) {
        if (!remoteBody() || blocked() || input.epoch() != state.epoch() || !input.valid()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        long window = mc.getWindow().getWindow();
        injecting = true;
        try {
            switch (input.kind()) {
                case Packets.Input.KEY -> {
                    if (input.action() == GLFW.GLFW_RELEASE) {
                        REMOTE_KEYS.remove(input.code());
                    } else {
                        REMOTE_KEYS.add(input.code());
                    }
                    ((KeyboardHandlerAccess) mc.keyboardHandler).duosight$key(
                            window, input.code(), input.scan(), input.action(), input.modifiers());
                }
                case Packets.Input.CHARACTER ->
                        ((KeyboardHandlerAccess) mc.keyboardHandler).duosight$character(
                                window, input.code(), input.modifiers());
                case Packets.Input.BUTTON ->
                        ((MouseHandlerAccess) mc.mouseHandler).duosight$press(
                                window, input.code(), input.action(), input.modifiers());
                case Packets.Input.LOOK -> {
                    if (mc.screen == null) {
                        mc.player.turn(input.x(), input.y());
                    }
                }
                case Packets.Input.CURSOR -> {
                    if (mc.screen != null) {
                        ((MouseHandlerAccess) mc.mouseHandler).duosight$move(window,
                                (mc.screen.width / 2.0 + input.x()) * mc.getWindow().getScreenWidth()
                                        / mc.getWindow().getGuiScaledWidth(),
                                (mc.screen.height / 2.0 + input.y()) * mc.getWindow().getScreenHeight()
                                        / mc.getWindow().getGuiScaledHeight());
                    }
                }
                case Packets.Input.SCROLL ->
                        ((MouseHandlerAccess) mc.mouseHandler).duosight$scroll(window, input.x(), input.y());
                default -> {}
            }
        } finally {
            injecting = false;
        }
    }

    @SubscribeEvent
    public static void beforeRender(TickEvent.RenderTickEvent.Pre event) {
        if (active() && driver() && !state.body() && !blocked()) {
            long now = System.nanoTime();
            if (now - lastMouseSend >= FRAME_INTERVAL) {
                flushMouse();
                lastMouseSend = now;
            }
        }
    }

    @SubscribeEvent
    public static void afterRender(TickEvent.RenderTickEvent.Post event) {
        if (!active() || !state.body() || state.travelling() || !loaded()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastPoseSend < FRAME_INTERVAL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || camera.getEntity() != mc.player) {
            return;
        }
        var position = camera.getPosition();
        Packets.server(new Packets.Pose(state.epoch(), state.dimension(), poseSequence++,
                position.x, position.y, position.z, camera.getYRot(), camera.getXRot()));
        lastPoseSend = now;
        if (!personalMenu && mc.screen instanceof AbstractContainerScreen<?> screen) {
            double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                    / mc.getWindow().getScreenWidth() - screen.width / 2.0;
            double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                    / mc.getWindow().getScreenHeight() - screen.height / 2.0;
            Packets.Cursor cursor = new Packets.Cursor(state.epoch(), state.dimension(),
                    screen.getMenu().containerId, x, y);
            if (!cursor.equals(lastCursor)) {
                Packets.server(cursor);
                lastCursor = cursor;
            }
        } else {
            lastCursor = null;
        }
    }

    public static void pose(Packets.Pose packet) {
        if (active() && !state.body() && !state.travelling() && packet.valid()
                && packet.epoch() == state.epoch() && packet.dimension().equals(state.dimension())
                && (latestPose == null || packet.sequence() > latestPose.sequence())) {
            latestPose = packet;
            poseReceived = System.nanoTime();
        }
    }

    public static Packets.Pose cameraPose() {
        return active() && !state.body() && !state.travelling() && loaded()
                && latestPose != null && System.nanoTime() - poseReceived < 500_000_000L
                ? latestPose : null;
    }

    public static void cursor(Packets.Cursor packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || state.body() || state.travelling()
                || !packet.valid() || packet.epoch() != state.epoch()
                || !packet.dimension().equals(state.dimension())) {
            return;
        }
        latestCursor = packet;
        if (personalMenu || !(mc.screen instanceof AbstractContainerScreen<?> screen)
                || packet.menuId() != screen.getMenu().containerId) {
            return;
        }
        ((MouseHandlerAccess) mc.mouseHandler).duosight$x(
                (screen.width / 2.0 + packet.x()) * mc.getWindow().getScreenWidth()
                        / mc.getWindow().getGuiScaledWidth());
        ((MouseHandlerAccess) mc.mouseHandler).duosight$y(
                (screen.height / 2.0 + packet.y()) * mc.getWindow().getScreenHeight()
                        / mc.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent.Post event) {
        GuideClient.tick();
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WinScreen credits && state.body()) {
            credits.onClose();
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!personalMenu && !state.travelling() && loaded() && guideHintTicks > 0) {
            guideHintTicks--;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.screen != null && !personalMenu) {
            GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
        if (state.travelling() && readyEpoch != state.epoch() && loaded()) {
            readyEpoch = state.epoch();
            Packets.server(new Packets.Ready(state.epoch(), state.dimension()));
        }
        if (resumeScreen && mc.screen == null && !state.travelling()) {
            resumeScreen = false;
            if (state.body() && savedScreen != null) {
                restoringScreen = true;
                try {
                    mc.setScreen(savedScreen);
                    if (savedScreen instanceof ContainerView container && savedContainerView != null) {
                        container.duosight$apply(savedContainerView);
                    }
                } finally {
                    restoringScreen = false;
                }
            } else if (!state.body() && latestView != null) {
                menuId = -1;
                menuKind = "";
                view(latestView);
            }
            savedScreen = null;
            savedContainerView = null;
        }
        if (!state.body()) {
            Entity body = mc.level.getEntity(state.bodyId());
            if (body != null && mc.getCameraEntity() != body) {
                mc.setCameraEntity(body);
            }
            if (!personalMenu) {
                KeyMapping.releaseAll();
            }
            if (driver() && !blocked()) {
                long now = System.nanoTime();
                if (now - lastMouseSend >= FRAME_INTERVAL) {
                    flushMouse();
                    lastMouseSend = now;
                }
            }
            return;
        }
        if (personalMenu || state.travelling() || !loaded()) {
            return;
        }
        CompoundTag snapshot = snapshot(mc);
        if (!snapshot.equals(lastView)) {
            Packets.server(new Packets.View(state.epoch(), snapshot));
            lastView = snapshot;
        }
    }

    private static CompoundTag snapshot(Minecraft mc) {
        var result = new CompoundTag();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            var menu = screen.getMenu();
            result.putInt("id", menu.containerId);
            if (screen instanceof InventoryScreen) {
                result.putString("menu", "inventory");
            } else if (menu instanceof HorseInventoryMenu) {
                result.putString("menu", "horse");
                var horse = ((HorseMenuAccess) menu).duosight$horse();
                result.putInt("horse", horse.getId());
                result.putInt("columns", horse.getInventoryColumns());
            } else {
                result.putString("menu", BuiltInRegistries.MENU.getKey(menu.getType()).toString());
            }
            if (screen instanceof RecipeUpdateListener recipes) {
                result.put("recipeView", ((RecipeView) recipes.getRecipeBookComponent()).duosight$capture());
            }
            if (screen instanceof ContainerView container) {
                result.put("containerView", container.duosight$capture());
            }
            result.putString("title", Component.Serializer.toJson(screen.getTitle(), mc.level.registryAccess()));
        } else {
            result.putString("menu", "");
        }
        return result;
    }

    private static ItemStack load(Minecraft mc, CompoundTag item) {
        return item.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(mc.level.registryAccess(), item);
    }

    public static void book(Packets.Book packet) {
        GuideClient.received();
        Minecraft mc = Minecraft.getInstance();
        if (packet.refresh() && !(mc.screen instanceof GuideScreen)) {
            return;
        }
        if (mc.player != null && mc.level != null) {
            var pages = packet.pages().stream().<Component>map(page ->
                    Component.Serializer.fromJson(page, mc.level.registryAccess())).toList();
            if (mc.screen instanceof GuideScreen screen) {
                screen.setBookAccess(new net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess(pages));
            } else {
                mc.setScreen(new GuideScreen(pages));
            }
        }
    }

    public static void view(Packets.View packet) {
        if (!active() || state.body() || state.travelling() || packet.epoch() != state.epoch()) {
            return;
        }
        latestView = packet;
        if (personalMenu || !loaded()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        CompoundTag data = packet.data();
        health = data.getFloat("health");
        food = data.getInt("food");
        level = data.getInt("level");
        mc.player.experienceLevel = level;
        mc.player.totalExperience = data.getInt("totalExperience");
        mc.player.experienceProgress = data.getFloat("experienceProgress");
        ListTag inventory = data.getList("inventory", 10);
        for (int i = 0; i < Math.min(inventory.size(), mc.player.getInventory().getContainerSize()); i++) {
            mc.player.getInventory().setItem(i, load(mc, inventory.getCompound(i)));
        }
        mc.player.getInventory().selected = Math.max(0, Math.min(8, data.getInt("selected")));
        ServerRecipeBook recipes = new ServerRecipeBook();
        recipes.fromNbt(data.getCompound("recipes"), mc.level.getRecipeManager());
        mc.player.getRecipeBook().copyOverData(recipes);
        String kind = data.getString("menu");
        int id = data.getInt("id");
        if (kind.isEmpty()) {
            if (remoteMenu) {
                mc.player.containerMenu = mc.player.inventoryMenu;
                mc.setScreen(null);
                remoteMenu = false;
                mouseKnown = false;
            }
            menuKind = "";
            menuId = -1;
            return;
        }
        if (!kind.equals(menuKind) || id != menuId) {
            mc.player.containerMenu = mc.player.inventoryMenu;
            if (kind.equals("inventory")) {
                mc.setScreen(new InventoryScreen(mc.player));
            } else if (kind.equals("horse")) {
                mc.getConnection().handleHorseScreenOpen(new ClientboundHorseScreenOpenPacket(id,
                        data.getInt("columns"), data.getInt("horse")));
            } else {
                var type = BuiltInRegistries.MENU.get(ResourceLocation.parse(kind));
                if (type == null) {
                    return;
                }
                mc.getConnection().handleOpenScreen(new ClientboundOpenScreenPacket(id, type,
                        Component.Serializer.fromJson(data.getString("title"), mc.level.registryAccess())));
            }
            menuId = id;
            menuKind = kind;
            remoteMenu = true;
            mouseKnown = false;
        }
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            if (screen instanceof RecipeUpdateListener listener) {
                CompoundTag recipeData = data.getCompound("recipeView");
                if (listener.getRecipeBookComponent().isVisible() != recipeData.getBoolean("open")) {
                    screen.init(mc, screen.width, screen.height);
                }
                ((RecipeView) listener.getRecipeBookComponent()).duosight$apply(recipeData);
            }
            AbstractContainerMenu menu = screen.getMenu();
            ListTag slots = data.getList("slots", 10);
            for (int i = 0; i < Math.min(slots.size(), menu.slots.size()); i++) {
                ItemStack item = load(mc, slots.getCompound(i));
                if (!ItemStack.matches(item, menu.slots.get(i).getItem())) {
                    menu.slots.get(i).set(item);
                }
            }
            menu.setCarried(load(mc, data.getCompound("carried")));
            int[] values = data.getIntArray("values");
            int count = ((MenuAccess) menu).duosight$data().size();
            for (int i = 0; i < Math.min(values.length, count); i++) {
                menu.setData(i, values[i]);
            }
            menu.broadcastChanges();
            if (screen instanceof ContainerView container) {
                container.duosight$apply(data.getCompound("containerView"));
            }
            if (latestCursor != null) {
                cursor(latestCursor);
            }
        }
    }

    public static void beforeScreen() {
        if (personalMenu() && (driver() || !loaded())) {
            blackFrame();
        }
    }

    private static void blackFrame() {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        RenderSystem.disableDepthTest();
        graphics.fill(RenderType.guiOverlay(), 0, 0, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), 0xFF000000);
        graphics.flush();
        RenderSystem.enableDepthTest();
    }

    public static void finishFrame() {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean chat = mc.screen instanceof ChatScreen;
        if (personalMenu && !chat) {
            return;
        }
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        RenderSystem.disableDepthTest();
        if (!chat && (driver() || (mc.level != null && !loaded()))) {
            graphics.fill(RenderType.guiOverlay(), 0, 0, width, height, 0xFF000000);
        } else if (!chat) {
            if (!state.body() && mc.screen == null) {
                graphics.drawCenteredString(mc.font, Component.translatable("duosight.vitals",
                        Math.round(health), food, level), width / 2, height - 46, 0xFFFFFF);
                graphics.fill(width / 2 - 2, height / 2, width / 2 + 3, height / 2 + 1, 0xFFFFFFFF);
                graphics.fill(width / 2, height / 2 - 2, width / 2 + 1, height / 2 + 3, 0xFFFFFFFF);
                if (mc.player != null) {
                    for (int i = 0; i < 9; i++) {
                        int x = width / 2 - 90 + i * 20;
                        boolean selected = i == mc.player.getInventory().selected;
                        graphics.fill(x, height - 24, x + 20, height - 4,
                                selected ? 0xCCFFFFFF : 0xAA252525);
                        ItemStack item = mc.player.getInventory().getItem(i);
                        graphics.renderItem(item, x + 2, height - 22);
                        graphics.renderItemDecorations(mc.font, item, x + 2, height - 22);
                    }
                }
            }
            if (mc.screen instanceof AbstractContainerScreen<?>) {
                int x = (int) (mc.mouseHandler.xpos() * width / mc.getWindow().getScreenWidth());
                int y = (int) (mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight());
                graphics.fill(RenderType.guiOverlay(), x - 3, y, x + 4, y + 1, 0xFF65E6B8);
                graphics.fill(RenderType.guiOverlay(), x, y - 3, x + 1, y + 4, 0xFF65E6B8);
            }
        }
        graphics.flush();
        String role = Component.translatable(state.travelling() ? "duosight.loading"
                : state.suspended() ? "duosight.paused"
                : driver() ? "duosight.driver" : "duosight.observer").getString();
        String timer = String.format("%02d:%02d", state.seconds() / 60, state.seconds() % 60);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        graphics.drawCenteredString(mc.font, role + "  " + timer, width / 2, 8, 0xFFFFFF);
        if (guideHintTicks > 0 && mc.screen == null && loaded()) {
            graphics.drawCenteredString(mc.font, Component.translatable("duosight.book.shortcut_hint",
                    Component.keybind("key.duosight.guide")), width / 2, 23, 0xFFFFFF);
        }
        graphics.flush();
        graphics.pose().popPose();
        RenderSystem.enableDepthTest();
    }

    private DuoClient() {}
}
