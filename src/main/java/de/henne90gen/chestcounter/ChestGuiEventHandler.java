package de.henne90gen.chestcounter;

import de.henne90gen.chestcounter.service.dtos.Chest;
import de.henne90gen.chestcounter.service.dtos.ChestSearchResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.inventory.container.Container;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChestGuiEventHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    private final ChestCounter mod;

    private Chest currentChest = null;

    private TextFieldWidget searchField = null;
    private ChestSearchResult lastSearchResult = null;

    private TextFieldWidget labelField = null;

    public ChestGuiEventHandler(ChestCounter mod) {
        this.mod = mod;
    }

    @SubscribeEvent
    public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof ContainerScreen)) {
            return;
        }

        LOGGER.debug("Opened container screen");

        ContainerScreen<?> screen = (ContainerScreen<?>) event.getGui();
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();
        int xSize = screen.getXSize();

        int x = guiLeft + xSize + Renderer.MARGIN;
        searchField = new TextFieldWidget(
                Minecraft.getInstance().fontRenderer,
                x, guiTop,
                100, 15,
                "Search"
        );
        event.addWidget(searchField);

        if (event.getGui() instanceof ChestScreen) {
            ChestScreen chestScreen = (ChestScreen) event.getGui();
            int width = 128;
            int numRows = chestScreen.getContainer().getNumRows();
            int offsetLeft = 40;
            if (numRows == 6) {
                // large chest
                offsetLeft = 75;
                width = 93;
            }
            int offsetTop = 5;
            labelField = new TextFieldWidget(
                    Minecraft.getInstance().fontRenderer,
                    guiLeft + offsetLeft, guiTop + offsetTop,
                    width, 10,
                    "Chest Label"
            );
            if (currentChest != null && currentChest.label != null) {
                labelField.setText(currentChest.label);
            }
            event.addWidget(labelField);
        }

        search();

    }

    @SubscribeEvent
    public void keyPressed(GuiScreenEvent.KeyboardKeyPressedEvent.Pre event) {
        if (!(event.getGui() instanceof ContainerScreen)) {
            return;
        }

        LOGGER.debug("Pressed key in container screen");

        keyPressedOnTextField(event, searchField);
        if (event.isCanceled()) {
            search();
            return;
        }

        if (event.getGui() instanceof ChestScreen) {
            keyPressedOnTextField(event, labelField);
            if (event.isCanceled()) {
                if (currentChest != null) {
                    mod.chestService.updateLabel(currentChest.worldId, currentChest.id, labelField.getText());
                }
                search();
            }
        }
    }

    private void search() {
        if (searchField == null) {
            return;
        }
        lastSearchResult = mod.chestService.getItemCounts(Helper.getWorldID(), searchField.getText());
    }

    private void keyPressedOnTextField(GuiScreenEvent.KeyboardKeyPressedEvent.Pre event, TextFieldWidget textField) {
        event.setCanceled(textField.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers()));
        boolean disallowedKeyPressed = event.getKeyCode() == 69/*e*/ || event.getKeyCode() == 76/*l*/;
        if (disallowedKeyPressed && textField.isFocused()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void charTyped(GuiScreenEvent.KeyboardCharTypedEvent.Pre event) {
        if (!(event.getGui() instanceof ContainerScreen)) {
            return;
        }

        LOGGER.debug("Typed char in container screen");
        if (event.getGui() instanceof InventoryScreen) {
            // chest does it automatically somehow
            // TODO this is buggy in chests (the last typed character is not picked up by the search)
            //  and if we do this in the chest screen as well, then it will display each typed character twice.
            event.setCanceled(searchField.charTyped(event.getCodePoint(), event.getModifiers()));
        }

        if (event.getGui() instanceof ChestScreen && currentChest != null) {
            event.setCanceled(labelField.charTyped(event.getCodePoint(), event.getModifiers()));
            mod.chestService.updateLabel(currentChest.worldId, currentChest.id, labelField.getText());
        }
        search();
    }

    @SubscribeEvent
    public void mouseClicked(GuiScreenEvent.MouseClickedEvent.Pre event) {
        if (!(event.getGui() instanceof ContainerScreen)) {
            return;
        }

        LOGGER.debug("Clicked mouse in container screen");
        searchField.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
        if (labelField != null) {
            labelField.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
        }
    }

    @SubscribeEvent
    public void mouseReleasedPre(GuiScreenEvent.MouseReleasedEvent.Pre event) {
        if (!(event.getGui() instanceof ContainerScreen)) {
            return;
        }

        searchField.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
        if (labelField != null) {
            labelField.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
        }

        // this only works for picking up items, not for dropping them back into the inventory
        saveCurrentChest(event);
        search();
    }

    private void saveCurrentChest(GuiScreenEvent event) {
        if (currentChest == null) {
            return;
        }
        if (event.getGui() instanceof ChestScreen) {
            Container currentContainer = ((ChestScreen) event.getGui()).getContainer();
            currentChest.items = Helper.countItemsInContainer(currentContainer);
            mod.chestService.save(currentChest);
        }
    }

    @SubscribeEvent
    public void renderSearchResult(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (lastSearchResult == null) {
            return;
        }

        if (!(event.getGui() instanceof ContainerScreen)) {
            return;
        }

        ContainerScreen<?> screen = (ContainerScreen<?>) event.getGui();
        Renderer.renderSearchResult(lastSearchResult, screen);
    }

    @SubscribeEvent
    public void blockClicked(PlayerInteractEvent.RightClickBlock event) {
        LOGGER.debug("Right clicked block");

        BlockPos pos = event.getPos();
        World world = event.getWorld();
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof ChestTileEntity)) {
            LOGGER.debug("Block was not a chest");
            return;
        }

        String worldId = Helper.getWorldID();
        String chestId = Helper.getChestId(world, pos);
        currentChest = mod.chestService.getChest(worldId, chestId);
        LOGGER.debug("Setting current chest to: " + currentChest.label + "(" + currentChest.id + ")");
    }
}
