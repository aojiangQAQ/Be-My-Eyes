package dev.duosight.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MouseHandler.class, remap = false)
public interface MouseHandlerAccess {
    @Accessor("isLeftPressed")
    void duosight$left(boolean pressed);

    @Accessor("isMiddlePressed")
    void duosight$middle(boolean pressed);

    @Accessor("isRightPressed")
    void duosight$right(boolean pressed);

    @Accessor("activeButton")
    void duosight$activeButton(int button);

    @Accessor("clickDepth")
    void duosight$clickDepth(int depth);

    @Accessor("mousePressedTime")
    void duosight$pressedTime(double time);

    @Invoker("onPress")
    void duosight$press(long window, int button, int action, int modifiers);

    @Invoker("onMove")
    void duosight$move(long window, double x, double y);

    @Invoker("onScroll")
    void duosight$scroll(long window, double x, double y);

    @Accessor("xpos")
    void duosight$x(double x);

    @Accessor("ypos")
    void duosight$y(double y);

    @Accessor("accumulatedDX")
    void duosight$dx(double value);

    @Accessor("accumulatedDY")
    void duosight$dy(double value);
}
