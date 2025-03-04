package dev.upcraft.soulbound.core;

import dev.upcraft.soulbound.Soulbound;
import dev.upcraft.soulbound.api.event.SoulboundFakePlayerCallback;
import dev.upcraft.soulbound.api.event.SoulboundItemCallback;
import dev.upcraft.soulbound.api.inventory.SoulboundContainer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;

import java.util.Random;
import java.util.function.UnaryOperator;

public class SoulboundHooks {

    public static boolean isRealPlayer(ServerPlayerEntity player) {
        return SoulboundFakePlayerCallback.EVENT.invoker().test(player);
    }

    public static UnaryOperator<ItemStack> createItemProcessor(SoulboundContainer container) {
        Random random = container.getEntity().getRandom();
        return stack -> {
            SoulboundItemCallback.Context ctx = new SoulboundItemCallback.Context(container, stack, Soulbound.CONFIG.get().soulboundPreservationRate);
            if (SoulboundItemCallback.EVENT.invoker().apply(ctx) != TriState.FALSE) {
                ItemStack itemStack = ctx.getStack();
                if (ctx.getLevelPreservationChance() <= random.nextDouble()) {
                    var map = EnchantmentHelper.get(itemStack);
                    int newLevel = map.getOrDefault(Soulbound.ENCHANT_SOULBOUND, 0) - 1;
                    if (newLevel > 0) {
                        map.put(Soulbound.ENCHANT_SOULBOUND, newLevel);
                    } else {
                        map.remove(Soulbound.ENCHANT_SOULBOUND);
                    }
                    EnchantmentHelper.set(map, itemStack);
                }
                return itemStack;
            }
            return ItemStack.EMPTY;
        };
    }

    public static void loadCompat(String modid, String clazz) {
        if (FabricLoader.getInstance().isModLoaded(modid)) {
            try {
                Class.forName(clazz);
            } catch (Exception e) {
                Soulbound.LOGGER.error("Failed to load {} compatibility from {}", modid, clazz, e);
            }
        }
    }

    public static NbtList getFilteredItemList(DefaultedList<ItemStack> items, Random random) {
        NbtList list = new NbtList();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (shouldKeepStack(stack, random)) {
                NbtCompound tag = new NbtCompound();
                tag.put("item", stack.writeNbt(new NbtCompound()));
                tag.putInt("slot", i);
                list.add(tag);
                items.set(i, ItemStack.EMPTY);
            }
        }
        return list;
    }

    public static boolean shouldKeepStack(ItemStack stack, Random random) {
        return EnchantmentHelper.getLevel(Soulbound.ENCHANT_SOULBOUND, stack) > 0 && Soulbound.CONFIG.get().soulboundDropChance <= random.nextDouble();
    }

    public static Int2ObjectMap<ItemStack> readItemList(NbtList list) {
        Int2ObjectMap<ItemStack> value = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(tag.getCompound("item"));
            value.put(tag.getInt("slot"), stack);
        }
        return value;
    }

    public static void processPlayerDrops(PlayerEntity player, DefaultedList<ItemStack> targetInv, Int2ObjectMap<ItemStack> items, UnaryOperator<ItemStack> itemProcessor) {
        items.int2ObjectEntrySet().forEach(e -> {
            ItemStack stack = itemProcessor.apply(e.getValue().copy());
            if (stack.isEmpty()) {
                ItemStack drop = e.getValue();
                drop.setCount(1);
                ItemEntity itemEntity = player.dropItem(drop, false);
                if (itemEntity != null) {
                    itemEntity.setDespawnImmediately();
                }
            } else {
                int slot = e.getIntKey();
                if (slot > 0 && slot < targetInv.size() && targetInv.get(slot).isEmpty()) {
                    targetInv.set(slot, stack);
                } else {
                    addItemToPlayer(player, stack);
                }
            }
        });
    }

    public static void addItemToPlayer(PlayerEntity player, ItemStack stack) {
        boolean inserted = player.getInventory().insertStack(stack);
        ItemEntity itemEntity;
        if (inserted && stack.isEmpty()) {
            stack.setCount(1);
            itemEntity = player.dropItem(stack, false);
            if (itemEntity != null) {
                itemEntity.setDespawnImmediately();
            }

            player.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F, ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            player.currentScreenHandler.sendContentUpdates();
        } else {
            itemEntity = player.dropItem(stack, false);
            if (itemEntity != null) {
                itemEntity.resetPickupDelay();
                itemEntity.setOwner(player.getUuid());
            }
        }
    }

}
