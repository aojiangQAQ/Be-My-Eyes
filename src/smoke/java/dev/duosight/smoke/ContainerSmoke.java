package dev.duosight.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import dev.duosight.client.ContainerView;
import dev.duosight.client.DuoClient;
import dev.duosight.mixin.KeyboardHandlerAccess;
import dev.duosight.mixin.MenuAccess;
import dev.duosight.mixin.MouseHandlerAccess;
import dev.duosight.net.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ContainerSmoke {
    private static final Block[] BLOCKS = {
            Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.GRINDSTONE, Blocks.SMITHING_TABLE,
            Blocks.STONECUTTER, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.BEACON,
            Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BREWING_STAND,
            Blocks.CHEST, Blocks.BARREL, Blocks.SHULKER_BOX, Blocks.HOPPER,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTER, Blocks.CRAFTING_TABLE
    };
    public static final int COUNT = BLOCKS.length;
    public static final int START = Integer.getInteger("duosight.containerStart", 0);
    private static CompoundTag expected;
    private static boolean acted, resumed, resultTaken, previewChecked;
    private static AbstractContainerMenu opened;
    private static BlockPos pos;
    private static Item stoneInput;

    public static void reset() {
        expected = null;
        acted = resumed = resultTaken = previewChecked = false;
    }

    public static void expected(String text) {
        try {
            expected = TagParser.parseTag(text);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    public static void open(ServerPlayer body, int index) {
        body.closeContainer();
        body.getInventory().clearContent();
        body.containerMenu.setCarried(ItemStack.EMPTY);
        pos = body.blockPosition().offset(1, 0, 0);
        var level = body.serverLevel();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, BLOCKS[index].defaultBlockState());
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider == null && level.getBlockEntity(pos) instanceof MenuProvider blockEntity) {
            provider = blockEntity;
        }
        check(provider != null && body.openMenu(provider).isPresent(), "open " + name(index));
        opened = body.containerMenu;
        if (opened instanceof StonecutterMenu) {
            stoneInput = List.of(Items.STONE, Items.SANDSTONE, Items.COPPER_BLOCK,
                            Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.TUFF)
                    .stream().max(Comparator.comparingInt(item -> level.getRecipeManager()
                            .getRecipesFor(net.minecraft.world.item.crafting.RecipeType.STONECUTTING,
                                    new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(item)),
                                    level).size())).orElseThrow();
        }
        if (opened instanceof BeaconMenu) {
            for (int tier = 1; tier <= 4; tier++) {
                for (int x = -tier; x <= tier; x++) {
                    for (int z = -tier; z <= tier; z++) {
                        level.setBlockAndUpdate(pos.offset(x, -tier, z), Blocks.IRON_BLOCK.defaultBlockState());
                    }
                }
            }
        }
        refill(body, index);
    }

    public static void refill(ServerPlayer body, int index) {
        body.setExperienceLevels(40);
        var menu = body.containerMenu;
        if (menu instanceof EnchantmentMenu) {
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 16));
        } else if (menu instanceof AnvilMenu) {
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        } else if (menu instanceof GrindstoneMenu) {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(body.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SHARPNESS), 1);
            menu.getSlot(0).set(sword);
        } else if (menu instanceof SmithingMenu) {
            menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND_SWORD));
            menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
        } else if (menu instanceof StonecutterMenu) {
            if (!menu.getSlot(0).hasItem()) {
                menu.getSlot(0).set(new ItemStack(stoneInput, 8));
            }
        } else if (menu instanceof LoomMenu) {
            menu.getSlot(0).set(new ItemStack(Items.WHITE_BANNER, 8));
            menu.getSlot(1).set(new ItemStack(Items.RED_DYE, 8));
        } else if (menu instanceof CartographyTableMenu) {
            ItemStack map = MapItem.create(body.serverLevel(), body.getBlockX(),
                    body.getBlockZ(), (byte) 0, true, false);
            var mapData = MapItem.getSavedData(map, body.serverLevel());
            mapData.setColor(64, 64, (byte) 20);
            mapData.getHoldingPlayer(body);
            body.connection.send(mapData.getUpdatePacket(map.get(DataComponents.MAP_ID), body));
            menu.getSlot(0).set(map);
            menu.getSlot(1).set(new ItemStack(Items.PAPER, 8));
        } else if (menu instanceof BeaconMenu) {
            menu.getSlot(0).set(new ItemStack(Items.EMERALD));
            menu.setData(0, 4);
        } else if (menu instanceof CrafterMenu) {
        } else if (menu instanceof CraftingMenu) {
            menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 8));
            menu.slotsChanged(menu.getSlot(1).container);
        } else if (menu instanceof BrewingStandMenu) {
            menu.getSlot(4).set(new ItemStack(Items.BLAZE_POWDER, 8));
        } else if (menu instanceof AbstractFurnaceMenu) {
            menu.getSlot(0).set(new ItemStack(index == 10 ? Items.PORKCHOP : Items.RAW_IRON, 8));
            menu.getSlot(1).set(new ItemStack(Items.COAL, 8));
        } else if (menu.getCarried().isEmpty()) {
            menu.getSlot(0).set(new ItemStack(Items.COBBLESTONE, 8));
        }
        menu.broadcastChanges();
        menu.sendAllDataToRemote();
    }

    public static void server(ServerPlayer body, ServerPlayer guest, int stage, int ticks) {
        int index = (stage - 100) / 3;
        int phase = (stage - 100) % 3;
        if (ticks < 50) {
            return;
        }
        var menu = body.containerMenu;
        if (phase == 2) {
            check(menu == body.inventoryMenu, "shared menu closed " + name(index));
            if (opened instanceof AnvilMenu) {
                check(body.getInventory().items.stream().anyMatch(item ->
                        item.getHoverName().getString().equals("Eyes Remote")), "anvil rename applied");
            } else if (opened instanceof BeaconMenu beacon) {
                check(beacon.getPrimaryEffect() != null && beacon.getPrimaryEffect().is(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("haste")),
                        "beacon confirmation applied");
            }
            return;
        }
        if (ticks % 5 != 0) {
            return;
        }
        check(menu == opened, "menu stays open through role swap " + name(index));
        if (menu instanceof EnchantmentMenu) {
            check(menu.getSlot(0).getItem().isEnchanted() && body.experienceLevel == 39,
                    "enchant applied and level charged");
        } else if (menu instanceof AnvilMenu anvil) {
            check(menu.getSlot(2).getItem().getHoverName().getString().equals(
                    phase == 0 ? "Eyes Local" : "Eyes Remote") && anvil.getCost() > 0,
                    "rename output/cost: " + menu.getSlot(2).getItem() + ", cost " + anvil.getCost());
        } else if (menu instanceof GrindstoneMenu) {
            check(body.getInventory().items.stream().anyMatch(item ->
                    item.is(Items.DIAMOND_SWORD) && !item.isEnchanted()), "grindstone output taken");
        } else if (menu instanceof SmithingMenu) {
            check(body.getInventory().items.stream().anyMatch(item ->
                    item.is(Items.NETHERITE_SWORD)), "smithing output taken");
        } else if (menu instanceof StonecutterMenu stone) {
            check(stone.getNumRecipes() > 12 && stone.getSelectedRecipeIndex() >= 4
                    && stone.getSlot(1).hasItem(), "scrolled stonecutter selection");
        } else if (menu instanceof LoomMenu loom) {
            check(loom.getSelectedBannerPatternIndex() >= 4 && loom.getResultSlot().hasItem(),
                    "scrolled loom selection");
        } else if (menu instanceof CrafterMenu crafter) {
            check(crafter.isSlotDisabled(0) == (phase == 0), "crafter slot toggle applied");
        } else if (!(menu instanceof BeaconMenu) && !(menu instanceof AbstractFurnaceMenu)
                && !(menu instanceof CraftingMenu) && !(menu instanceof CartographyTableMenu)) {
            check(menu.getCarried().isEmpty() == (phase == 1), "slot click applied " + name(index)
                    + ", carried " + menu.getCarried() + ", input " + menu.getSlot(menu instanceof BrewingStandMenu ? 4 : 0).getItem());
        }
        CompoundTag data = new CompoundTag();
        data.putInt("stage", stage);
        data.putInt("id", menu.containerId);
        data.putString("type", BuiltInRegistries.MENU.getKey(menu.getType()).toString());
        data.putInt("level", body.experienceLevel);
        ListTag slots = new ListTag();
        menu.slots.forEach(slot -> slots.add(save(body, slot.getItem())));
        data.put("slots", slots);
        data.put("carried", save(body, menu.getCarried()));
        data.putIntArray("values", ((MenuAccess) menu).duosight$data().stream().mapToInt(slot -> (short) slot.get()).toArray());
        for (ServerPlayer player : new ServerPlayer[]{body, guest}) {
            player.sendSystemMessage(Component.literal("BMECONTAINER:" + data));
        }
    }

    public static boolean client(Minecraft mc, Packets.State state, int stage, int ticks) throws Exception {
        int index = (stage - 100) / 3;
        int phase = (stage - 100) % 3;
        if (state.travelling() || state.driver() != (phase == 0 ? state.body() : !state.body())) {
            return false;
        }
        if (phase == 2) {
            if (state.driver() && mc.screen instanceof AbstractContainerScreen<?> screen) {
                if (!resultTaken) {
                    if (screen instanceof AnvilScreen) {
                        clickSlot(mc, screen, 2, true);
                    } else if (screen instanceof BeaconScreen) {
                        clickAt(mc, screen.getGuiLeft() + 175, screen.getGuiTop() + 118);
                    }
                    resultTaken = true;
                } else if (ticks > 30 && !acted) {
                    press(mc, GLFW.GLFW_KEY_ESCAPE, 0);
                    acted = true;
                }
            }
            return ticks > 70 && mc.screen == null;
        }
        if (index == 1 && phase == 1 && state.body()) {
            if (ticks == 30) {
                press(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            } else if (ticks == 40 && !resumed) {
                mc.screen.onClose();
                resumed = true;
            }
        }
        if (state.suspended() || !(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        var menu = screen.getMenu();
        if (menu instanceof CartographyTableMenu && ticks >= 10 && !previewChecked) {
            var map = MapItem.getSavedData(menu.getSlot(0).getItem(), mc.level);
            if (map == null) {
                check(ticks < 180, "cartography map preview data received");
                return false;
            }
            check(map.colors[64 + 64 * 128] == 20, "cartography preview pixels match");
            previewChecked = true;
            if (!state.driver() && !resultTaken) {
                Files.createDirectories(Path.of("smoke-screenshots"));
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(Path.of("smoke-screenshots", "map-preview-" + phase + ".png"));
                }
                resultTaken = true;
            }
        }
        if (ticks >= (menu instanceof CartographyTableMenu ? 35 : 15) && !acted && state.driver()) {
            System.out.println("BE_MY_EYES_CONTAINER_ACTION: " + name(index) + " phase " + phase
                    + ", carried " + menu.getCarried());
            if (menu instanceof AnvilMenu) {
                for (int i = 0; i < 50; i++) {
                    press(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
                }
                String text = phase == 0 ? "Eyes Local" : "Eyes Remote";
                for (int c : text.codePoints().toArray()) {
                    ((KeyboardHandlerAccess) mc.keyboardHandler).duosight$character(mc.getWindow().getWindow(), c, 0);
                }
            } else if (menu instanceof EnchantmentMenu) {
                clickAt(mc, screen.getGuiLeft() + 90, screen.getGuiTop() + 22);
            } else if (menu instanceof GrindstoneMenu || menu instanceof SmithingMenu
                    || menu instanceof CraftingMenu || menu instanceof CartographyTableMenu) {
                int slot = menu instanceof SmithingMenu ? 3 : menu instanceof CraftingMenu ? 0 : 2;
                check(menu.getSlot(slot).hasItem(), "output available " + name(index));
                clickSlot(mc, screen, slot, true);
            } else if (menu instanceof StonecutterMenu || menu instanceof LoomMenu) {
                move(mc, screen.getGuiLeft() + 80, screen.getGuiTop() + 30);
                ((MouseHandlerAccess) mc.mouseHandler).duosight$scroll(mc.getWindow().getWindow(), 0, -1);
            } else if (menu instanceof BeaconMenu) {
                clickAt(mc, screen.getGuiLeft() + (phase == 0 ? 64 : 88), screen.getGuiTop() + 33);
                clickAt(mc, screen.getGuiLeft() + (phase == 0 ? 155 : 179), screen.getGuiTop() + 58);
            } else if (!(menu instanceof AbstractFurnaceMenu)) {
                clickSlot(mc, screen, menu instanceof BrewingStandMenu ? 4 : 0, false);
            } else {
                move(mc, screen.getGuiLeft() + 60, screen.getGuiTop() + 40);
            }
            acted = true;
        }
        if (acted && !resultTaken && ticks >= 25 && state.driver()
                && (menu instanceof StonecutterMenu || menu instanceof LoomMenu)) {
            clickAt(mc, screen.getGuiLeft() + (menu instanceof LoomMenu ? 66 : 60),
                    screen.getGuiTop() + 20);
            resultTaken = true;
        }
        if (index == 12 && phase == 0 && ticks >= 40 && state.driver() && !resultTaken) {
            ((MouseHandlerAccess) mc.mouseHandler).duosight$press(
                    mc.getWindow().getWindow(), 0, GLFW.GLFW_PRESS, 0);
            resultTaken = true;
        }
        if (ticks < 75 || expected == null || expected.getInt("stage") != stage
                || expected.getInt("id") != menu.containerId) {
            return false;
        }
        check(mc.player.experienceLevel == expected.getInt("level"), "shared experience " + name(index));
        ListTag slots = expected.getList("slots", 10);
        for (int i = 0; i < slots.size(); i++) {
            if (!ItemStack.matches(load(mc, slots.getCompound(i)), menu.getSlot(i).getItem())) {
                check(ticks < 180, "slot " + i + " synchronized " + name(index));
                return false;
            }
        }
        if (!ItemStack.matches(load(mc, expected.getCompound("carried")), menu.getCarried())) {
            return false;
        }
        int[] values = expected.getIntArray("values");
        var data = ((MenuAccess) menu).duosight$data();
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - data.get(i).get()) > (menu instanceof AbstractFurnaceMenu ? 6 : 0)) {
                check(ticks < 180, "data " + i + " synchronized " + name(index));
                return false;
            }
        }
        if (screen instanceof ContainerView container) {
            CompoundTag view = container.duosight$capture();
            if (menu instanceof AnvilMenu) {
                check(view.getString("name").equals(phase == 0 ? "Eyes Local" : "Eyes Remote"),
                        "anvil edit text mirrored");
            } else if (menu instanceof StonecutterMenu || menu instanceof LoomMenu) {
                check(view.getFloat("scroll") > 0 && view.getInt("start") >= 1, "scroll position mirrored");
            } else if (menu instanceof BeaconMenu) {
                check(view.getString("primary").equals(phase == 0 ? "minecraft:speed" : "minecraft:haste")
                        && view.getString("secondary").equals(phase == 0 ? "minecraft:regeneration" : "minecraft:haste"),
                        "pending beacon effects mirrored");
            }
        }
        if (screen instanceof SmithingScreen) {
            Field preview = SmithingScreen.class.getDeclaredField("armorStandPreview");
            preview.setAccessible(true);
            check(preview.get(screen) != null, "smithing preview initialized");
        }
        if (!state.driver()) {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int visible = 0, cursor = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int rgb = image.getPixelRGBA(x, y) & 0xFFFFFF;
                        if (rgb != 0) visible++;
                        if (rgb == 0xB8E665) cursor++;
                    }
                }
                check(visible > 1000 && cursor > 5, "container and cursor rendered " + name(index));
                Files.createDirectories(Path.of("smoke-screenshots"));
                image.writeToFile(Path.of("smoke-screenshots", "container-" + index + "-" + phase + ".png"));
            }
        } else {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                VisualSmoke.checkDriverFrame(mc, image);
            }
        }
        System.out.println("BE_MY_EYES_CONTAINER_PASS: " + name(index) + " "
                + (state.body() ? "body" : "guest") + " phase " + phase);
        return true;
    }

    private static String name(int index) {
        return BuiltInRegistries.BLOCK.getKey(BLOCKS[index]).getPath();
    }

    private static CompoundTag save(ServerPlayer player, ItemStack item) {
        return item.isEmpty() ? new CompoundTag() : (CompoundTag) item.save(player.registryAccess());
    }

    private static ItemStack load(Minecraft mc, CompoundTag item) {
        return item.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(mc.level.registryAccess(), item);
    }

    private static void clickSlot(Minecraft mc, AbstractContainerScreen<?> screen, int index, boolean shift) {
        Slot slot = screen.getMenu().getSlot(index);
        boolean remoteShift = shift && !DuoClient.isBody();
        if (remoteShift) key(mc, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_SHIFT);
        clickAt(mc, screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8);
        if (remoteShift) {
            key(mc, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE, 0);
        } else if (shift) {
            Slot target = screen.getMenu().slots.stream().filter(candidate ->
                    candidate.container == mc.player.getInventory() && !candidate.hasItem())
                    .findFirst().orElseThrow();
            clickAt(mc, screen.getGuiLeft() + target.x + 8, screen.getGuiTop() + target.y + 8);
        }
    }

    private static void key(Minecraft mc, int key, int action, int mods) {
        ((KeyboardHandlerAccess) mc.keyboardHandler).duosight$key(mc.getWindow().getWindow(), key, 0, action, mods);
    }

    private static void press(Minecraft mc, int key, int mods) {
        key(mc, key, GLFW.GLFW_PRESS, mods);
        key(mc, key, GLFW.GLFW_RELEASE, mods);
    }

    private static void move(Minecraft mc, double x, double y) {
        ((MouseHandlerAccess) mc.mouseHandler).duosight$move(mc.getWindow().getWindow(),
                x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
    }

    private static void clickAt(Minecraft mc, double x, double y) {
        move(mc, x, y);
        var mouse = (MouseHandlerAccess) mc.mouseHandler;
        mouse.duosight$press(mc.getWindow().getWindow(), 0, GLFW.GLFW_PRESS, 0);
        mouse.duosight$press(mc.getWindow().getWindow(), 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError("Be My Eyes container test: " + message);
    }

    private ContainerSmoke() {}
}
