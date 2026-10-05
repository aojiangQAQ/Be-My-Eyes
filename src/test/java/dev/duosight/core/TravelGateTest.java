package dev.duosight.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelGateTest {
    @Test
    void requiresBothClientsInTheCurrentDimension() {
        var gate = new TravelGate();
        gate.begin(2, "minecraft:the_nether");
        assertFalse(gate.ready(true, 2, "minecraft:the_nether"));
        assertTrue(gate.waiting());
        assertFalse(gate.ready(false, 2, "minecraft:overworld"));
        assertFalse(gate.ready(false, 1, "minecraft:the_nether"));
        assertTrue(gate.waiting());
        assertTrue(gate.ready(false, 2, "minecraft:the_nether"));
        assertFalse(gate.waiting());
    }

    @Test
    void aSecondTravelResetsBothAcknowledgements() {
        var gate = new TravelGate();
        gate.begin(2, "minecraft:the_end");
        gate.ready(true, 2, "minecraft:the_end");
        gate.ready(false, 2, "minecraft:the_end");
        gate.begin(3, "minecraft:overworld");
        assertTrue(gate.waiting());
        assertFalse(gate.ready(false, 2, "minecraft:the_end"));
        assertFalse(gate.ready(false, 3, "minecraft:overworld"));
        assertTrue(gate.ready(true, 3, "minecraft:overworld"));
    }

    @Test
    void duplicateAcknowledgementsCannotSubstituteForTheOtherClient() {
        var gate = new TravelGate();
        gate.begin(8, "minecraft:overworld");
        assertFalse(gate.ready(true, 8, "minecraft:overworld"));
        assertFalse(gate.ready(true, 8, "minecraft:overworld"));
        assertTrue(gate.waiting());
    }
}
