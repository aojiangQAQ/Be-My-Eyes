package dev.duosight.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class GuidePages {
    public record Shared(String partner, boolean driver, int interval, int remaining) {}
    public record Invitation(String inviter, boolean inviterDrives, int interval) {}

    public static List<Component> create(PairingOptions options, Shared shared,
                                         Invitation invitation, List<String> players) {
        return create(options, shared, invitation, players, true);
    }

    public static List<Component> create(PairingOptions options, Shared shared,
                                         Invitation invitation, List<String> players, boolean canReceiveBook) {
        var pages = new ArrayList<Component>();
        MutableComponent home = Component.literal("")
                .append(Component.literal("Be My Eyes").withStyle(ChatFormatting.BOLD))
                .append("\n\n");
        if (shared == null) {
            home.append(text("initial_role")).append("\n")
                    .append(action("driver", "role driver", options.driver())).append("  ")
                    .append(action("observer", "role eyes", !options.driver())).append("\n\n");
            intervals(home, options.seconds());
            home.append(pageLink("custom", 2)).append("\n")
                    .append(pageLink("invite", 3)).append("\n");
        } else {
            home.append(text("partner")).append("\n").append(shared.partner()).append("\n")
                    .append(Component.translatable("duosight.book.role",
                            Component.translatable(shared.driver() ? "duosight.driver" : "duosight.observer")))
                    .append("\n")
                    .append(Component.translatable("duosight.book.remaining", shared.remaining()))
                    .append("\n\n");
            intervals(home, shared.interval());
            home.append(pageLink("custom", 2)).append("\n")
                    .append(action("swap", "swap", false)).append("\n")
                    .append(action("stop", "stop", false).withStyle(ChatFormatting.DARK_RED)).append("\n");
        }
        home.append(action("refresh", "book", false));
        pages.add(home);
        pages.add(custom(shared == null ? options.seconds() : shared.interval()));
        if (shared == null) {
            if (players.isEmpty()) {
                pages.add(partners().append(text("no_players")).append("\n\n")
                        .append(pageLink("back", 1)));
            } else {
                for (int offset = 0; offset < players.size(); offset += 6) {
                    MutableComponent page = partners();
                    for (String name : players.subList(offset, Math.min(offset + 6, players.size()))) {
                        page.append(link(Component.literal(name), "invite " + name, false)).append("\n");
                    }
                    page.append("\n").append(pageLink("back", 1));
                    pages.add(page);
                }
            }
            if (invitation != null) {
                home.append("\n").append(pageLink("invitation", pages.size() + 1));
                pages.add(Component.literal("").append(text("invitation").withStyle(ChatFormatting.BOLD))
                        .append("\n\n").append(invitation.inviter()).append("\n")
                        .append(text("shared_body")).append("\n\n")
                        .append(Component.translatable("duosight.book.role", Component.translatable(
                                invitation.inviterDrives() ? "duosight.observer" : "duosight.driver")))
                        .append("\n").append(Component.translatable("duosight.book.every", invitation.interval()))
                        .append("\n\n").append(action("accept", "accept", false)).append("\n")
                        .append(action("decline", "decline", false)).append("\n")
                        .append(pageLink("back", 1)));
            }
            if (canReceiveBook) {
                home.append("\n").append(action("get", "book give", false));
            }
        }
        return List.copyOf(pages);
    }

    private static MutableComponent partners() {
        return Component.literal("").append(text("nearby").withStyle(ChatFormatting.BOLD))
                .append("\n\n");
    }

    private static MutableComponent custom(int seconds) {
        MutableComponent page = Component.literal("").append(text("custom").withStyle(ChatFormatting.BOLD))
                .append("\n\n").append(Component.translatable("duosight.book.every", seconds)).append("\n\n");
        for (int delta : new int[]{-60, -10, -1, 1, 10, 60}) {
            MutableComponent label = Component.literal(delta > 0 ? "+" + delta : Integer.toString(delta));
            if (delta < 0 && seconds == 15 || delta > 0 && seconds == 3600) {
                page.append(label.withStyle(ChatFormatting.GRAY));
            } else {
                page.append(link(label, "interval add " + delta, false));
            }
            page.append(delta == -1 || delta == 60 ? "\n" : "  ");
        }
        return page.append("\n").append(pageLink("back", 1));
    }

    private static void intervals(MutableComponent page, int selected) {
        page.append(Component.translatable("duosight.book.every", selected)).append("\n");
        int[] choices = {15, 30, 60, 120, 300, 600};
        for (int i = 0; i < choices.length; i++) {
            int seconds = choices[i];
            page.append(link(Component.literal(Integer.toString(seconds)), "interval " + seconds,
                    seconds == selected));
            page.append(i == 2 || i == 5 ? "\n" : "  ");
        }
    }

    private static MutableComponent text(String key) {
        return Component.translatable("duosight.book." + key);
    }

    private static MutableComponent action(String key, String command, boolean selected) {
        String translation = key.equals("driver") || key.equals("observer") ? "duosight." + key
                : "duosight.book." + key;
        return link(Component.translatable(translation), command, selected);
    }

    private static MutableComponent link(MutableComponent label, String command, boolean selected) {
        return label.withStyle(style -> style.withColor(selected ? ChatFormatting.DARK_GREEN : ChatFormatting.BLUE)
                .withUnderlined(true).withBold(selected)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bemyeyes " + command)));
    }

    private static MutableComponent pageLink(String key, int page) {
        return text(key).withStyle(style -> style.withColor(ChatFormatting.BLUE).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, Integer.toString(page))));
    }

    private GuidePages() {}
}
