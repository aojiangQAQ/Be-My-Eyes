package dev.duosight.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EditBox.class, remap = false)
public interface EditBoxAccess {
    @Accessor("highlightPos")
    int duosight$highlight();

    @Accessor("displayPos")
    int duosight$display();

    @Accessor("displayPos")
    void duosight$display(int position);
}
