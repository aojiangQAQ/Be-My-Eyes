package dev.duosight.mixin;

import dev.duosight.client.ContainerInput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(value = AbstractContainerScreen.class, remap = false)
public abstract class ContainerScreenMixin implements ContainerInput {
    @Shadow @Final protected Set<Slot> quickCraftSlots;
    @Shadow protected boolean isQuickCrafting;
    @Shadow private boolean skipNextRelease;
    @Shadow private boolean doubleclick;
    @Shadow private long lastClickTime;
    @Shadow private Slot clickedSlot;
    @Shadow private Slot lastClickSlot;
    @Shadow private ItemStack draggingItem;
    @Shadow private ItemStack lastQuickMoved;

    @Override
    public void duosight$resetInput() {
        quickCraftSlots.clear();
        isQuickCrafting = false;
        skipNextRelease = true;
        doubleclick = false;
        lastClickTime = 0;
        clickedSlot = lastClickSlot = null;
        draggingItem = lastQuickMoved = ItemStack.EMPTY;
    }
}
