package dev.duosight.core;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public record GuideAction(Kind kind, int value, String player) {
    public enum Kind {
        ROLE, INTERVAL, ADJUST, INVITE, ACCEPT, DECLINE, SWAP, STOP, GIVE, REFRESH
    }

    public boolean valid() {
        if (kind == null || player == null) {
            return false;
        }
        if (kind == Kind.INVITE) {
            return value == 0 && player.matches("[A-Za-z0-9_]{1,16}");
        }
        return player.isEmpty() && switch (kind) {
            case ROLE -> value == 0 || value == 1;
            case INTERVAL -> value >= 15 && value <= 3600;
            case ADJUST -> value != 0 && value >= -3600 && value <= 3600;
            default -> value == 0;
        };
    }

    public boolean closes() {
        return kind == Kind.INVITE || kind == Kind.ACCEPT || kind == Kind.DECLINE || kind == Kind.STOP;
    }

    public String command() {
        return "bemyeyes " + switch (kind) {
            case ROLE -> "role " + (value == 1 ? "driver" : "eyes");
            case INTERVAL -> "interval " + value;
            case ADJUST -> "interval add " + value;
            case INVITE -> "invite " + player;
            case ACCEPT -> "accept";
            case DECLINE -> "decline";
            case SWAP -> "swap";
            case STOP -> "stop";
            case GIVE -> "book give";
            case REFRESH -> "book";
        };
    }

    public static GuideAction fromClick(String command) {
        if (command == null) {
            return null;
        }
        StringReader reader = new StringReader(command);
        try {
            reader.expect('/');
            if (!reader.readUnquotedString().equals("bemyeyes")) {
                return null;
            }
            reader.skipWhitespace();
            String operation = reader.readUnquotedString();
            reader.skipWhitespace();
            GuideAction action = switch (operation) {
                case "role" -> {
                    String role = reader.readUnquotedString();
                    yield role.equals("driver") ? new GuideAction(Kind.ROLE, 1, "")
                            : role.equals("eyes") ? new GuideAction(Kind.ROLE, 0, "") : null;
                }
                case "interval" -> {
                    boolean adjust = reader.canRead() && reader.peek() == 'a';
                    if (adjust && !reader.readUnquotedString().equals("add")) {
                        yield null;
                    }
                    reader.skipWhitespace();
                    yield new GuideAction(adjust ? Kind.ADJUST : Kind.INTERVAL, reader.readInt(), "");
                }
                case "invite" -> new GuideAction(Kind.INVITE, 0, reader.readUnquotedString());
                case "accept" -> new GuideAction(Kind.ACCEPT, 0, "");
                case "decline" -> new GuideAction(Kind.DECLINE, 0, "");
                case "swap" -> new GuideAction(Kind.SWAP, 0, "");
                case "stop" -> new GuideAction(Kind.STOP, 0, "");
                case "book" -> !reader.canRead() ? new GuideAction(Kind.REFRESH, 0, "")
                        : reader.readUnquotedString().equals("give") ? new GuideAction(Kind.GIVE, 0, "") : null;
                default -> null;
            };
            reader.skipWhitespace();
            return action != null && action.valid() && !reader.canRead() ? action : null;
        } catch (CommandSyntaxException exception) {
            return null;
        }
    }
}
