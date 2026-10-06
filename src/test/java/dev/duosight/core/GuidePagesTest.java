package dev.duosight.core;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuidePagesTest {
    @Test
    void validatesOpeningIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new PairingOptions(true, 14));
        assertThrows(IllegalArgumentException.class, () -> new PairingOptions(false, 3601));
        assertEquals(15, new PairingOptions(false, 15).seconds());
        assertEquals(3600, new PairingOptions(true, 3600).seconds());
    }

    @Test
    void homeHasRoleAndIntervalActionsBeforePairing() {
        var pages = GuidePages.create(new PairingOptions(false, 30), null, null, List.of("Friend"));
        List<String> commands = commands(pages.getFirst());
        assertTrue(commands.contains("/bemyeyes role driver"));
        assertTrue(commands.contains("/bemyeyes role eyes"));
        for (int seconds : new int[]{15, 30, 60, 120, 300, 600}) {
            assertTrue(commands.contains("/bemyeyes interval " + seconds));
        }
        assertTrue(commands.contains("/bemyeyes book"));
        assertTrue(commands.contains("/bemyeyes book give"));
        assertEquals(List.of("/bemyeyes invite Friend"), commands(pages.get(2)));
    }

    @Test
    void invitationIsShownOnlyWithAnExplicitAcceptOrDecline() {
        var pages = GuidePages.create(new PairingOptions(true, 120), null,
                new GuidePages.Invitation("Friend", false, 30), List.of());
        assertEquals(4, pages.size());
        assertTrue(commands(pages.getLast()).containsAll(List.of("/bemyeyes accept", "/bemyeyes decline")));
        assertFalse(commands(pages.getFirst()).contains("/bemyeyes accept"));
        assertEquals("4", clicks(pages.getFirst()).stream()
                .filter(click -> click.getAction() == ClickEvent.Action.CHANGE_PAGE)
                .toList().getLast().getValue());
    }

    @Test
    void partneredMenuDoesNotOfferInvitingOrChangingInitialRole() {
        var pages = GuidePages.create(new PairingOptions(true, 120),
                new GuidePages.Shared("Friend", false, 30, 17), null, List.of("Other"));
        assertEquals(2, pages.size());
        List<String> commands = commands(pages.getFirst());
        assertTrue(commands.containsAll(List.of("/bemyeyes swap", "/bemyeyes stop")));
        assertFalse(commands.stream().anyMatch(command -> command.contains(" invite ")
                || command.contains(" role ") || command.endsWith("book give")));
    }

    @Test
    void splitsNearbyPlayersIntoShortPagesWithoutDroppingNames() {
        var names = java.util.stream.IntStream.range(0, 19).mapToObj(i -> "Player" + i).toList();
        var pages = GuidePages.create(new PairingOptions(true, 120), null, null, names);
        assertEquals(6, pages.size());
        assertEquals(names.stream().map(name -> "/bemyeyes invite " + name).toList(),
                pages.stream().skip(2).flatMap(page -> commands(page).stream()).toList());
        assertTrue(pages.stream().skip(2).allMatch(page -> commands(page).size() <= 6));
    }

    @Test
    void customPageProvidesFineAndCoarseAdjustments() {
        var pages = GuidePages.create(new PairingOptions(true, 120), null, null, List.of());
        assertEquals(List.of(-60, -10, -1, 1, 10, 60).stream()
                .map(delta -> "/bemyeyes interval add " + delta).toList(), commands(pages.get(1)));
        assertEquals("1", clicks(pages.get(1)).getLast().getValue());
        assertTrue(pages.stream().flatMap(page -> commands(page).stream())
                .allMatch(command -> GuideAction.fromClick(command) != null));
    }

    @Test
    void customPageDisablesAdjustmentsAtTheBounds() {
        var minimum = GuidePages.create(new PairingOptions(true, 15), null, null, List.of()).get(1);
        var maximum = GuidePages.create(new PairingOptions(true, 3600), null, null, List.of()).get(1);
        assertEquals(List.of("/bemyeyes interval add 1", "/bemyeyes interval add 10",
                "/bemyeyes interval add 60"), commands(minimum));
        assertEquals(List.of("/bemyeyes interval add -60", "/bemyeyes interval add -10",
                "/bemyeyes interval add -1"), commands(maximum));
    }

    @Test
    void playedPlayersAreNotOfferedPhysicalBooks() {
        var pages = GuidePages.create(new PairingOptions(true, 120), null, null, List.of(), false);
        assertFalse(commands(pages.getFirst()).contains("/bemyeyes book give"));
    }

    private static List<String> commands(Component page) {
        return clicks(page).stream().filter(click -> click.getAction() == ClickEvent.Action.RUN_COMMAND)
                .map(ClickEvent::getValue).toList();
    }

    private static List<ClickEvent> clicks(Component component) {
        var result = new ArrayList<ClickEvent>();
        if (component.getStyle().getClickEvent() != null) {
            result.add(component.getStyle().getClickEvent());
        }
        component.getSiblings().forEach(child -> result.addAll(clicks(child)));
        return result;
    }
}
