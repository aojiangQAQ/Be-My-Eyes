package dev.duosight.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

public final class DuoChatScreen extends ChatScreen {
    private final int openingKey;
    private boolean openingCharacter = true;

    public DuoChatScreen(String initial, int openingKey) {
        super(initial);
        this.openingKey = openingKey;
    }

    public boolean openingCharacter() {
        boolean skip = openingCharacter;
        openingCharacter = false;
        return skip;
    }

    public void openingKeyReleased(int key) {
        if (key == openingKey) {
            openingCharacter = false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        super.render(graphics, x, y, partialTick);
        graphics.pose().popPose();
    }
}
