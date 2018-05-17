package de.henne90gen.chestcounter;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ChestEventHandler {

	private static final int INVENTORY_SIZE = 36;

	private final List<BlockPos> chestPositions;

	public ChestEventHandler() {
		this.chestPositions = new ArrayList<>();
	}

	@SubscribeEvent
	public void open(GuiContainerEvent event) {
		if (shouldNotHandleEvent(event)) {
			return;
		}

		Minecraft mc = FMLClientHandler.instance().getClient();
		if (mc.currentScreen instanceof GuiContainer) {
			Container currentContainer = ((GuiContainer) mc.currentScreen).inventorySlots;

			Chest chest = new Chest();
			chest.id = ItemDB.buildID(chestPositions);
			chest.items = countItems(currentContainer);
			ItemDB.save(chest);
		}

		chestPositions.clear();
	}

	private Map<String, Integer> countItems(Container currentContainer) {
		Map<String, Integer> counter = new LinkedHashMap<>();
		for (int i = 0; i < currentContainer.inventorySlots.size() - INVENTORY_SIZE; i++) {
			ItemStack stack = currentContainer.inventorySlots.get(i).getStack();
			String itemName = stack.getDisplayName();
			if ("Air".equals(itemName)) {
				continue;
			}
			Integer currentCount = counter.get(itemName);
			if (currentCount == null) {
				currentCount = 0;
			}
			currentCount += stack.getCount();
			counter.put(itemName, currentCount);
		}
		return counter;
	}

	@SubscribeEvent
	public void command(ClientChatEvent event) {
		if (event.getMessage().startsWith("/chest")) {
			CommandHandler.query(event.getMessage());
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void interact(PlayerInteractEvent event) {
		if (!event.getWorld().isRemote) {
			return;
		}

		chestPositions.clear();
		addChestPosition(event, event.getPos());
		addChestPosition(event, event.getPos().north());
		addChestPosition(event, event.getPos().east());
		addChestPosition(event, event.getPos().south());
		addChestPosition(event, event.getPos().west());
	}

	@SubscribeEvent
	public void harvestBlock(BlockEvent.BreakEvent event) {
		TileEntity tileEntity = event.getWorld().getTileEntity(event.getPos());
		if (tileEntity instanceof TileEntityChest) {
			ItemDB.delete(ItemDB.buildID(Collections.singletonList(event.getPos())));
		}
	}

	private void addChestPosition(PlayerInteractEvent event, BlockPos position) {
		TileEntity tileEntity = event.getWorld().getTileEntity(position);
		if (tileEntity instanceof TileEntityChest) {
			chestPositions.add(position);
		}
	}

	private boolean shouldNotHandleEvent(GuiContainerEvent event) {
		return event.getGuiContainer() == null ||
				event.getGuiContainer().mc == null ||
				event.getGuiContainer().mc.world == null ||
				!event.getGuiContainer().mc.world.isRemote ||
				chestPositions.isEmpty();
	}
}
