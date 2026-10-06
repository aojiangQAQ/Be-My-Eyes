package dev.duosight.core;

public final class SwapClock {
    private int interval;
    private int remaining;
    private int epoch;
    private boolean bodyControls = true;

    public SwapClock(int intervalSeconds) {
        this(intervalSeconds, true);
    }

    public SwapClock(int intervalSeconds, boolean bodyControls) {
        setInterval(intervalSeconds);
        this.bodyControls = bodyControls;
    }

    public void setInterval(int intervalSeconds) {
        if (intervalSeconds < 1 || intervalSeconds > 3600) {
            throw new IllegalArgumentException("Invalid interval");
        }
        interval = intervalSeconds * 20;
        remaining = interval;
    }

    public int intervalSeconds() {
        return interval / 20;
    }

    public boolean tick() {
        if (--remaining > 0) {
            return false;
        }
        swap();
        return true;
    }

    public void swap() {
        bodyControls = !bodyControls;
        invalidate();
        remaining = interval;
    }

    public void invalidate() {
        epoch++;
    }

    public int seconds() {
        return (remaining + 19) / 20;
    }

    public int epoch() {
        return epoch;
    }

    public boolean bodyControls() {
        return bodyControls;
    }
}
