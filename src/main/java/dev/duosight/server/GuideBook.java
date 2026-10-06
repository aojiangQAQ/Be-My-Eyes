package dev.duosight.server;

import dev.duosight.DuoConfig;
import dev.duosight.DuoSight;
import dev.duosight.core.PairingOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GuideBook {
    public static final String DATA = "duosight_guide";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DuoSight.ID);
    public static final RegistryObject<Item> ITEM = ITEMS.register("guide", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                Sessions.book(serverPlayer);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
    });

    public static PairingOptions options(ServerPlayer player) {
        CompoundTag data = player.getPersistentData().getCompound(DATA);
        int seconds = data.getInt("seconds");
        if (seconds < 15 || seconds > 3600) {
            seconds = DuoConfig.SWAP_SECONDS.get();
        }
        return new PairingOptions(!data.contains("driver") || data.getBoolean("driver"), seconds);
    }

    public static void options(ServerPlayer player, PairingOptions options) {
        CompoundTag data = data(player);
        data.putBoolean("driver", options.driver());
        data.putInt("seconds", options.seconds());
    }

    public static void login(ServerPlayer player) {
        if (played(player)) {
            remove(player);
        } else if (!data(player).getBoolean("received")) {
            give(player);
        }
    }

    public static boolean played(ServerPlayer player) {
        return player.getPersistentData().getCompound(DATA).getBoolean("played");
    }

    public static void consume(ServerPlayer player) {
        data(player).putBoolean("received", true);
        data(player).putBoolean("played", true);
        remove(player);
    }

    static void remove(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ITEM.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        for (var menu : new AbstractContainerMenu[]{player.inventoryMenu, player.containerMenu}) {
            for (var slot : menu.slots) {
                if (slot.container instanceof CraftingContainer && slot.getItem().is(ITEM.get())) {
                    slot.set(ItemStack.EMPTY);
                }
            }
        }
        if (player.containerMenu.getCarried().is(ITEM.get())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (player.inventoryMenu.getCarried().is(ITEM.get())) {
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    public static int give(ServerPlayer player) {
        if (played(player)) {
            player.sendSystemMessage(Component.translatable("duosight.book.give_disabled",
                    Component.keybind("key.duosight.guide")));
            return 0;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ITEM.get())) {
                data(player).putBoolean("received", true);
                return 1;
            }
        }
        if (!player.getInventory().add(new ItemStack(ITEM.get()))) {
            player.sendSystemMessage(Component.translatable("duosight.book.full"));
            return 0;
        }
        data(player).putBoolean("received", true);
        player.inventoryMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable("duosight.book.received",
                Component.keybind("key.duosight.guide")).withStyle(style ->
                style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bemyeyes book"))));
        return 1;
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA)) {
            root.put(DATA, new CompoundTag());
        }
        return root.getCompound(DATA);
    }

    private GuideBook() {}
}
