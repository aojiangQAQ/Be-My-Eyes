package dev.duosight.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = RecipeBookPage.class, remap = false)
public interface RecipePageAccess {
    @Accessor("currentPage")
    int duosight$page();

    @Accessor("currentPage")
    void duosight$page(int page);

    @Accessor("totalPages")
    int duosight$pages();

    @Invoker("updateButtonsForPage")
    void duosight$refresh();
}
