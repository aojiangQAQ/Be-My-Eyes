package dev.duosight.client;

import dev.duosight.core.GuideAction;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;

public final class GuideScreen extends BookViewScreen {
    private int page;
    private int pageCount;

    public GuideScreen(List<Component> pages) {
        super(new BookAccess(pages));
        pageCount = pages.size();
    }

    @Override
    public void setBookAccess(BookAccess access) {
        pageCount = access.getPageCount();
        super.setBookAccess(access);
        setPage(page);
    }

    @Override
    public boolean setPage(int next) {
        page = Math.max(0, Math.min(pageCount - 1, next));
        return super.setPage(page);
    }

    @Override
    protected void pageBack() {
        setPage(page - 1);
    }

    @Override
    protected void pageForward() {
        setPage(page + 1);
    }

    @Override
    public boolean handleComponentClicked(Style style) {
        ClickEvent click = style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND
                || !click.getValue().startsWith("/bemyeyes ") || minecraft.getConnection() == null) {
            return super.handleComponentClicked(style);
        }
        GuideAction action = GuideAction.fromClick(click.getValue());
        if (GuideClient.action(action) && action.closes()) {
            onClose();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
