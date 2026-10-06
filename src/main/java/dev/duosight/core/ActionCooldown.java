package dev.duosight.core;

public final class ActionCooldown {
    private final long interval;
    private long last;
    private boolean used;

    public ActionCooldown(long interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Invalid cooldown");
        }
        this.interval = interval;
    }

    public boolean allow(long now) {
        if (used && now - last < interval) {
            return false;
        }
        last = now;
        used = true;
        return true;
    }

    public void reset() {
        used = false;
    }
}
