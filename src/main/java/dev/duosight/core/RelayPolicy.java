package dev.duosight.core;

public record RelayPolicy(int epoch, String dimension, boolean bodySource,
                          boolean guestControls, boolean suspended, boolean travelling) {
    public boolean input(int packetEpoch) {
        return !bodySource && guestControls && !suspended && packetEpoch == epoch;
    }

    public boolean visual(int packetEpoch, String packetDimension) {
        return bodySource && !travelling && packetEpoch == epoch && dimension.equals(packetDimension);
    }
}
