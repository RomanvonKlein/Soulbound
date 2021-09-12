package dev.upcraft.soulbound.core.mixin;

import com.google.common.collect.Lists;
import dev.upcraft.soulbound.api.SlottedItem;
import dev.upcraft.soulbound.Soulbound;
import dev.upcraft.soulbound.api.SoulboundContainer;
import dev.upcraft.soulbound.core.SoulboundPersistentState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = PlayerEntity.class, priority = 6969)
public class MixinPlayerEntity {

    @Inject(method = "dropInventory", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;dropAll()V"))
    private void soulbound$dropInventory(CallbackInfo callbackInfo) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if ( player.getServer() == null)
            return;

        SoulboundPersistentState persistentState =  SoulboundPersistentState.get(player.getServer());
        List<SlottedItem> soulboundItems = Lists.newArrayList();
        SoulboundContainer.CONTAINERS.forEach((id, container) -> {
            List<ItemStack> inventory = container.getContainerStacks(player);
            if (inventory == null)
                return;

            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (stack.isEmpty())
                    continue;

                int soulboundLevel = EnchantmentHelper.getLevel(Soulbound.ENCHANT_SOULBOUND, stack);
                if (soulboundLevel > 0) {
                    soulboundItems.add(new SlottedItem(id, stack, i));
                    container.removeStoredItem(player, i);
                }
            }
        });

        persistentState.storePlayer(player, soulboundItems);
    }
}
