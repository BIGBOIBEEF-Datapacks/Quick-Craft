package com.bigboibeef.quickcraft;

import com.bigboibeef.quickcraft.commands.Mode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class QuickCraft implements ClientModInitializer {
	public static final String MOD_ID = "quick-craft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyBinding QUICK_CRAFT;
	private static final ItemStack[] GRID9 = new ItemStack[9];
	private static final ItemStack[] GRID4 = new ItemStack[4];
	public static boolean single;
	public static boolean enabled;

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.create();
	private static final Path SAVE_FILE = Paths.get("qc-config.json");
	private static Map<String, Boolean> info = new HashMap<>();

	@Override
	public void onInitializeClient() {
		LOGGER.info("Quick Craft initialized.");
		Mode.register();
		single = true;
		enabled = true;
		Arrays.fill(GRID9, ItemStack.EMPTY);
		Arrays.fill(GRID4, ItemStack.EMPTY);
		QUICK_CRAFT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.quickcraft.quick_craft",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_SPACE,
				"category.quickcraft"
		));

		loadData();
		saveData();
	}

	public static void saveGrid(MinecraftClient client) {
		if (client.currentScreen instanceof HandledScreen<?> screen) {
			ScreenHandler handler = screen.getScreenHandler();

			if (handler instanceof PlayerScreenHandler) {
				//2x2 inv
				for (int i = 1; i <= 4; i++) {
					ItemStack inSlot = screen.getScreenHandler().getSlot(i).getStack();
					if (!inSlot.isEmpty()) {
						ItemStack oneCopy = inSlot.copy();
						oneCopy.setCount(1);
						GRID4[i - 1] = oneCopy;
					} else {
						GRID4[i - 1] = ItemStack.EMPTY;
					}
				}
			} else if (handler instanceof CraftingScreenHandler) {
				//3x3 crafting table
				for (int i = 1; i <= 9; i++) {
					ItemStack inSlot = screen.getScreenHandler().getSlot(i).getStack();
					if (!inSlot.isEmpty()) {
						ItemStack oneCopy = inSlot.copy();
						oneCopy.setCount(1);
						GRID9[i - 1] = oneCopy;
					} else {
						GRID9[i - 1] = ItemStack.EMPTY;
					}
				}
			}
		}
	}

	public static void loadSingleGrid(MinecraftClient client) {
		if (client.currentScreen instanceof HandledScreen<?> screen) {
			ClientPlayerEntity player = client.player;
			ScreenHandler handler = screen.getScreenHandler();

			if (handler instanceof PlayerScreenHandler) {
				if (player == null || player.currentScreenHandler == null) return;
				ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
				if (net == null) return;

				int syncId = handler.syncId;

				for (int slotIndex = 1; slotIndex <= 4; slotIndex++) {
					Slot craftSlot = handler.getSlot(slotIndex);
					if (craftSlot == null || !craftSlot.hasStack()) continue;

					net.sendPacket(new ClickSlotC2SPacket(
							syncId, handler.nextRevision(), craftSlot.id, 0,
							SlotActionType.QUICK_MOVE, ItemStack.EMPTY,
							Int2ObjectMaps.emptyMap()
					));
				}

				Map<ItemStack, LinkedHashMap<Integer, Integer>> inventoryByItem = groupInventoryItems(player, handler);

				for (int gridIndex = 0; gridIndex < 4; gridIndex++) {
					ItemStack needed = GRID4[gridIndex];
					if (needed.isEmpty()) continue;

					Slot craftSlot = handler.getSlot(gridIndex + 1);
					if (craftSlot == null) continue;

					for (Map.Entry<ItemStack, LinkedHashMap<Integer, Integer>> entry : inventoryByItem.entrySet()) {
						ItemStack candidate = entry.getKey();
						if (ItemStack.areItemsEqual(candidate, needed) && ItemStack.areItemsAndComponentsEqual(candidate, needed)) {
							Iterator<Map.Entry<Integer, Integer>> it = entry.getValue().entrySet().iterator();

							while (it.hasNext()) {
								Map.Entry<Integer, Integer> slotEntry = it.next();
								int invSlot = slotEntry.getKey();
								int count = slotEntry.getValue();

								if (count > 0) {
									int invSlotId = handler.getSlot(invSlot).id;
									int craftSlotId = craftSlot.id;

									ItemStack invStack = handler.getSlot(invSlot).getStack();

									net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, invStack, Int2ObjectMaps.emptyMap()));
									net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), craftSlotId, 1, SlotActionType.PICKUP, invStack.copyWithCount(1), Int2ObjectMaps.emptyMap()));
									net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, invStack, Int2ObjectMaps.emptyMap()));

									if (--count <= 0) it.remove();
									else slotEntry.setValue(count);
									break;
								}
							}
							break;
						}
					}
				}

				player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
			} else if (handler instanceof CraftingScreenHandler) {
				if (player == null || player.currentScreenHandler == null) return;
				ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
				if (net == null) return;

				int syncId = handler.syncId;

				for (int slotIndex = 1; slotIndex <= 9; slotIndex++) {
					Slot craftSlot = handler.getSlot(slotIndex);
					if (craftSlot == null || !craftSlot.hasStack()) continue;

					net.sendPacket(new ClickSlotC2SPacket(
							syncId, handler.nextRevision(), craftSlot.id, 0,
							SlotActionType.QUICK_MOVE, ItemStack.EMPTY,
							Int2ObjectMaps.emptyMap()
					));
				}

				Map<ItemStack, LinkedHashMap<Integer, Integer>> inventoryByItem = groupInventoryItems(player, handler);

				for (int gridIndex = 0; gridIndex < 9; gridIndex++) {
					ItemStack needed = GRID9[gridIndex];
					if (needed.isEmpty()) continue;

					Slot craftSlot = handler.getSlot(gridIndex + 1);
					if (craftSlot == null) continue;

					for (Map.Entry<ItemStack, LinkedHashMap<Integer, Integer>> entry : inventoryByItem.entrySet()) {
						ItemStack candidate = entry.getKey();
						if (ItemStack.areItemsEqual(candidate, needed) && ItemStack.areItemsAndComponentsEqual(candidate, needed)) {
							Iterator<Map.Entry<Integer, Integer>> it = entry.getValue().entrySet().iterator();

							while (it.hasNext()) {
								Map.Entry<Integer, Integer> slotEntry = it.next();
								int invSlot = slotEntry.getKey();
								int count = slotEntry.getValue();

								if (count > 0) {
									int invSlotId = handler.getSlot(invSlot).id;
									int craftSlotId = craftSlot.id;

									ItemStack invStack = handler.getSlot(invSlot).getStack();

									net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, invStack, Int2ObjectMaps.emptyMap()));
									net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), craftSlotId, 1, SlotActionType.PICKUP, invStack.copyWithCount(1), Int2ObjectMaps.emptyMap()));
									net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, invStack, Int2ObjectMaps.emptyMap()));

									if (--count <= 0) it.remove();
									else slotEntry.setValue(count);
									break;
								}
							}
							break;
						}
					}
				}

				player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
			}
		}
	}

	public static void loadStackGrid(MinecraftClient client) {
		if (client.currentScreen instanceof HandledScreen<?> screen) {
			ClientPlayerEntity player = client.player;
			ScreenHandler handler = screen.getScreenHandler();

			if (handler instanceof CraftingScreenHandler) {
				if (player == null || player.currentScreenHandler == null) return;
				ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
				if (net == null) return;

				int syncId = handler.syncId;

				for (int slotIndex = 1; slotIndex <= 9; slotIndex++) {
					Slot craftSlot = handler.getSlot(slotIndex);
					if (craftSlot == null || !craftSlot.hasStack()) continue;

					net.sendPacket(new ClickSlotC2SPacket(
							syncId, handler.nextRevision(), craftSlot.id, 0,
							SlotActionType.QUICK_MOVE, ItemStack.EMPTY,
							Int2ObjectMaps.emptyMap()
					));
				}

				Map<ItemStack, LinkedHashMap<Integer, Integer>> inventoryByItem = groupInventoryItems(player, handler);

				List<Integer> gridIndices = new ArrayList<>();
				Map<ItemStack, Integer> totalAvailable = new HashMap<>();

				for (int i = 0; i < 9; i++) {
					ItemStack needed = GRID9[i];
					if (needed.isEmpty()) continue;

					gridIndices.add(i);

					int count = 0;
					for (Map.Entry<ItemStack, LinkedHashMap<Integer, Integer>> entry : inventoryByItem.entrySet()) {
						ItemStack candidate = entry.getKey();
						if (!ItemStack.areItemsEqual(candidate, needed) || !ItemStack.areItemsAndComponentsEqual(candidate, needed)) continue;

						for (int c : entry.getValue().values()) count += c;
					}

					totalAvailable.put(needed, totalAvailable.getOrDefault(needed, 0) + count);
				}

				for (int i : gridIndices) {
					ItemStack needed = GRID9[i];
					if (totalAvailable.getOrDefault(needed, 0) < 1) return;
				}

				int maxPerSlot = 64;
				int targetPerSlot = Integer.MAX_VALUE;

				for (Map.Entry<ItemStack, Integer> e : totalAvailable.entrySet()) {
					long matchingSlots = gridIndices.stream().filter(i -> ItemStack.areItemsEqual(GRID9[i], e.getKey()) && ItemStack.areItemsAndComponentsEqual(GRID9[i], e.getKey())).count();
					if (matchingSlots > 0) {
						targetPerSlot = Math.min(targetPerSlot, e.getValue() / (int) matchingSlots);
					}
				}

				targetPerSlot = Math.min(targetPerSlot, maxPerSlot);

				for (int pass = 0; pass < targetPerSlot; pass++) {
					for (int gridIndex : gridIndices) {
						ItemStack needed = GRID9[gridIndex];
						Slot craftSlot = handler.getSlot(gridIndex + 1);
						if (craftSlot == null) continue;
						int craftSlotId = craftSlot.id;

						for (Map.Entry<ItemStack, LinkedHashMap<Integer, Integer>> entry : inventoryByItem.entrySet()) {
							ItemStack candidate = entry.getKey();
							if (!ItemStack.areItemsEqual(candidate, needed) || !ItemStack.areItemsAndComponentsEqual(candidate, needed)) continue;

							Iterator<Map.Entry<Integer, Integer>> it = entry.getValue().entrySet().iterator();

							while (it.hasNext()) {
								Map.Entry<Integer, Integer> slotEntry = it.next();
								int invSlot = slotEntry.getKey();
								int count = slotEntry.getValue();

								if (count <= 0) continue;

								int invSlotId = handler.getSlot(invSlot).id;
								ItemStack fullStack = handler.getSlot(invSlot).getStack().copy();

								net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, fullStack, Int2ObjectMaps.emptyMap()));
								net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), craftSlotId, 1, SlotActionType.PICKUP, fullStack.copyWithCount(1), Int2ObjectMaps.emptyMap()));
								net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, fullStack.copyWithCount(count - 1), Int2ObjectMaps.emptyMap()));

								if (--count <= 0) it.remove();
								else slotEntry.setValue(count);

								break;
							}

							break;
						}
					}
				}

				player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
			} else if (handler instanceof PlayerScreenHandler) {
				if (player == null || player.currentScreenHandler == null) return;
				ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
				if (net == null) return;

				int syncId = handler.syncId;

				for (int slotIndex = 1; slotIndex <= 4; slotIndex++) {
					Slot craftSlot = handler.getSlot(slotIndex);
					if (craftSlot == null || !craftSlot.hasStack()) continue;

					net.sendPacket(new ClickSlotC2SPacket(
							syncId, handler.nextRevision(), craftSlot.id, 0,
							SlotActionType.QUICK_MOVE, ItemStack.EMPTY,
							Int2ObjectMaps.emptyMap()
					));
				}

				Map<ItemStack, LinkedHashMap<Integer, Integer>> inventoryByItem = groupInventoryItems(player, handler);

				List<Integer> gridIndices = new ArrayList<>();
				Map<ItemStack, Integer> totalAvailable = new HashMap<>();

				for (int i = 0; i < 4; i++) {
					ItemStack needed = GRID4[i];
					if (needed.isEmpty()) continue;

					gridIndices.add(i);

					int count = 0;
					for (Map.Entry<ItemStack, LinkedHashMap<Integer, Integer>> entry : inventoryByItem.entrySet()) {
						ItemStack candidate = entry.getKey();
						if (!ItemStack.areItemsEqual(candidate, needed) || !ItemStack.areItemsAndComponentsEqual(candidate, needed)) continue;

						for (int c : entry.getValue().values()) count += c;
					}

					totalAvailable.put(needed, totalAvailable.getOrDefault(needed, 0) + count);
				}

				for (int i : gridIndices) {
					ItemStack needed = GRID4[i];
					if (totalAvailable.getOrDefault(needed, 0) < 1) return;
				}

				int maxPerSlot = 64;
				int targetPerSlot = Integer.MAX_VALUE;

				for (Map.Entry<ItemStack, Integer> e : totalAvailable.entrySet()) {
					long matchingSlots = gridIndices.stream().filter(i -> ItemStack.areItemsEqual(GRID9[i], e.getKey()) && ItemStack.areItemsAndComponentsEqual(GRID9[i], e.getKey())).count();
					if (matchingSlots > 0) {
						targetPerSlot = Math.min(targetPerSlot, e.getValue() / (int) matchingSlots);
					}
				}

				targetPerSlot = Math.min(targetPerSlot, maxPerSlot);

				for (int pass = 0; pass < targetPerSlot; pass++) {
					for (int gridIndex : gridIndices) {
						ItemStack needed = GRID4[gridIndex];
						Slot craftSlot = handler.getSlot(gridIndex + 1);
						if (craftSlot == null) continue;
						int craftSlotId = craftSlot.id;

						for (Map.Entry<ItemStack, LinkedHashMap<Integer, Integer>> entry : inventoryByItem.entrySet()) {
							ItemStack candidate = entry.getKey();
							if (!ItemStack.areItemsEqual(candidate, needed) || !ItemStack.areItemsAndComponentsEqual(candidate, needed)) continue;

							Iterator<Map.Entry<Integer, Integer>> it = entry.getValue().entrySet().iterator();

							while (it.hasNext()) {
								Map.Entry<Integer, Integer> slotEntry = it.next();
								int invSlot = slotEntry.getKey();
								int count = slotEntry.getValue();

								if (count <= 0) continue;

								int invSlotId = handler.getSlot(invSlot).id;
								ItemStack fullStack = handler.getSlot(invSlot).getStack().copy();

								net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, fullStack, Int2ObjectMaps.emptyMap()));
								net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), craftSlotId, 1, SlotActionType.PICKUP, fullStack.copyWithCount(1), Int2ObjectMaps.emptyMap()));
								net.sendPacket(new ClickSlotC2SPacket(syncId, handler.nextRevision(), invSlotId, 0, SlotActionType.PICKUP, fullStack.copyWithCount(count - 1), Int2ObjectMaps.emptyMap()));

								if (--count <= 0) it.remove();
								else slotEntry.setValue(count);

								break;
							}

							break;
						}
					}
				}

				player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
			}
		}
	}

	private static Map<ItemStack, LinkedHashMap<Integer, Integer>> groupInventoryItems(ClientPlayerEntity player, ScreenHandler handler) {
		Map<ItemStack, LinkedHashMap<Integer, Integer>> inventoryByItem = new LinkedHashMap<>();

		for (int i = 10; i < handler.slots.size(); i++) {
			Slot s = handler.getSlot(i);
			ItemStack stack = s.getStack();
			if (s.inventory == player.getInventory() && !stack.isEmpty()) {
				boolean found = false;
				for (ItemStack key : inventoryByItem.keySet()) {
					if (ItemStack.areItemsEqual(key, stack) && ItemStack.areItemsAndComponentsEqual(key, stack)) {
						inventoryByItem.get(key).put(i, stack.getCount());
						found = true;
						break;
					}
				}
				if (!found) {
					LinkedHashMap<Integer, Integer> slotMap = new LinkedHashMap<>();
					slotMap.put(i, stack.getCount());
					inventoryByItem.put(stack.copy(), slotMap);
				}
			}
		}
		return inventoryByItem;
	}

	public static boolean isQuickCraftKey(int keyCode, int scanCode) {
		loadData();
		return QUICK_CRAFT.matchesKey(keyCode, scanCode);
	}

	public static void enable () {
		enabled = true;
		saveData();
	}

	public static void disable () {
		enabled = false;
		saveData();
	}


	//MODE = [0] ENABLED =[1]
	public static void loadData() {
		if (Files.exists(SAVE_FILE)) {
			try (Reader reader = Files.newBufferedReader(SAVE_FILE)) {
				Type type = new TypeToken<Map<String, Boolean>>(){}.getType();
				Map<String, Boolean> loaded = GSON.fromJson(reader, type);
				if (loaded != null) {
					info = loaded;
					single = info.get("mode");
					enabled = info.get("enabled");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void saveData() {
		try (Writer writer = Files.newBufferedWriter(SAVE_FILE)) {
			info.put("mode", single);
			info.put("enabled", enabled);
			GSON.toJson(info, writer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
