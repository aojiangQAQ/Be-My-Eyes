package dev.duosight.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SharedMaps {
    private final Map<MapId, Snapshot> previous = new HashMap<>();

    void clear() {
        previous.clear();
    }

    void send(ServerPlayer body, ServerPlayer guest) {
        Set<MapId> ids = new HashSet<>();
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            collect(ids, body.getInventory().getItem(i));
        }
        body.containerMenu.slots.forEach(slot -> collect(ids, slot.getItem()));
        collect(ids, body.containerMenu.getCarried());
        previous.keySet().retainAll(ids);
        for (MapId id : ids) {
            MapItemSavedData map = MapItem.getSavedData(id, body.serverLevel());
            if (map == null) continue;
            Snapshot before = previous.get(id);
            List<MapDecoration> decorations = new ArrayList<>();
            map.getDecorations().forEach(decorations::add);
            var patch = patch(before == null ? null : before.colors, map.colors);
            boolean markersChanged = before == null || !before.decorations.equals(decorations);
            if (patch != null || markersChanged || before.scale != map.scale || before.locked != map.locked) {
                guest.connection.send(new ClientboundMapItemDataPacket(id, map.scale, map.locked,
                        markersChanged ? decorations : null, patch));
                previous.put(id, new Snapshot(patch == null ? before.colors : map.colors.clone(),
                        map.scale, map.locked, List.copyOf(decorations)));
            }
        }
    }

    private static void collect(Set<MapId> ids, ItemStack item) {
        MapId id = item.get(DataComponents.MAP_ID);
        if (id != null) ids.add(id);
    }

    static MapItemSavedData.MapPatch patch(byte[] before, byte[] after) {
        if (before == null) {
            return new MapItemSavedData.MapPatch(0, 0, 128, 128, after.clone());
        }
        int minX = 128, minY = 128, maxX = -1, maxY = -1;
        for (int i = 0; i < after.length; i++) {
            if (before[i] != after[i]) {
                int x = i % 128, y = i / 128;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) return null;
        int width = maxX - minX + 1, height = maxY - minY + 1;
        byte[] colors = new byte[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(after, (minY + y) * 128 + minX, colors, y * width, width);
        }
        return new MapItemSavedData.MapPatch(minX, minY, width, height, colors);
    }

    private record Snapshot(byte[] colors, byte scale, boolean locked, List<MapDecoration> decorations) {}
}
