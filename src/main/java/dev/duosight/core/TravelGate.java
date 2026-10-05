package dev.duosight.core;

public final class TravelGate {
    private int epoch;
    private String dimension = "";
    private boolean bodyReady, guestReady;

    public void begin(int epoch, String dimension) {
        this.epoch = epoch;
        this.dimension = dimension;
        bodyReady = guestReady = false;
    }

    public boolean ready(boolean body, int epoch, String dimension) {
        if (this.epoch != epoch || !this.dimension.equals(dimension)) {
            return false;
        }
        if (body) {
            bodyReady = true;
        } else {
            guestReady = true;
        }
        return !waiting();
    }

    public boolean waiting() {
        return !bodyReady || !guestReady;
    }
}
