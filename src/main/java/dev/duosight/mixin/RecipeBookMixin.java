package dev.duosight.mixin;

import dev.duosight.client.RecipeView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = RecipeBookComponent.class, remap = false)
public abstract class RecipeBookMixin implements RecipeView {
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;
    @Shadow private RecipeBookTabButton selectedTab;
    @Shadow private EditBox searchBox;
    @Shadow @Final private RecipeBookPage recipeBookPage;
    @Shadow @Final protected GhostRecipe ghostRecipe;
    @Shadow protected StateSwitchingButton filterButton;
    @Shadow protected Minecraft minecraft;
    @Shadow protected RecipeBookMenu<?, ?> menu;
    @Shadow public abstract boolean isVisible();
    @Shadow private void updateStackedContents() {}
    @Shadow private void updateFilterButtonTooltip() {}

    @Override
    public boolean duosight$editing() {
        return isVisible() && searchBox != null && searchBox.canConsumeInput();
    }

    @Override
    public CompoundTag duosight$capture() {
        CompoundTag data = new CompoundTag();
        data.putBoolean("open", isVisible());
        data.putString("search", searchBox == null ? "" : searchBox.getValue());
        data.putBoolean("focused", searchBox != null && searchBox.isFocused());
        data.putString("tab", selectedTab == null ? "" : selectedTab.getCategory().name());
        data.putInt("page", ((RecipePageAccess) recipeBookPage).duosight$page());
        data.putString("ghost", ghostRecipe.getRecipe() == null ? "" : ghostRecipe.getRecipe().id().toString());
        return data;
    }

    @Override
    public void duosight$apply(CompoundTag data) {
        if (isVisible() && searchBox != null) {
            searchBox.setValue(data.getString("search"));
            searchBox.setFocused(data.getBoolean("focused"));
            for (RecipeBookTabButton tab : tabButtons) {
                if (tab.getCategory().name().equals(data.getString("tab"))) {
                    if (selectedTab != null) {
                        selectedTab.setStateTriggered(false);
                    }
                    selectedTab = tab;
                    tab.setStateTriggered(true);
                    break;
                }
            }
            filterButton.setStateTriggered(minecraft.player.getRecipeBook().isFiltering(menu));
            updateFilterButtonTooltip();
            updateStackedContents();
            ((RecipeBookComponent) (Object) this).recipesUpdated();
            RecipePageAccess page = (RecipePageAccess) recipeBookPage;
            page.duosight$page(Math.max(0, Math.min(data.getInt("page"), page.duosight$pages() - 1)));
            page.duosight$refresh();
        }
        String ghost = data.getString("ghost");
        String current = ghostRecipe.getRecipe() == null ? "" : ghostRecipe.getRecipe().id().toString();
        if (!ghost.equals(current)) {
            ghostRecipe.clear();
            if (!ghost.isEmpty()) {
                minecraft.level.getRecipeManager().byKey(ResourceLocation.parse(ghost)).ifPresent(
                        recipe -> ((RecipeBookComponent) (Object) this).setupGhostRecipe(recipe, menu.slots));
            }
        }
    }
}
