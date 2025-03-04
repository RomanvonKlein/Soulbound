package dev.upcraft.soulbound.core.inventory;

import dev.upcraft.soulbound.api.inventory.SoulboundContainerProvider;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class PlayerInventoryContainerProvider implements SoulboundContainerProvider<PlayerInventoryContainer> {
    @Override
    public @Nullable PlayerInventoryContainer getContainer(LivingEntity entity) {
        return entity instanceof PlayerInventoryContainer ? (PlayerInventoryContainer) entity : null;
    }
}
