package dev.duosight.core;

public record PairingOptions(boolean driver, int seconds) {
    public PairingOptions {
        if (seconds < 15 || seconds > 3600) {
            throw new IllegalArgumentException("Invalid pairing interval");
        }
    }

    public PairingOptions adjust(int delta) {
        int adjusted = (int) Math.max(15, Math.min(3600, seconds + (long) delta));
        return new PairingOptions(driver, adjusted);
    }
}
