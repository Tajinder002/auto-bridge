package dev.autobridge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class AutoBridgeScreen extends Screen {
    private static final int PANEL_WIDTH = 310;
    private static final int PANEL_HEIGHT = 250;

    public AutoBridgeScreen() { super(Text.literal("Auto-Bridge")); }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int buttonWidth = 140;
        int gap = 10;
        int row1 = top + 55;
        int row2 = top + 88;
        int row3 = top + 121;
        int row4 = top + 154;

        addDrawableChild(ButtonWidget.builder(toggleText("Bridge Assist", BridgeConfig.bridgeAssist), button -> {
            BridgeConfig.bridgeAssist = !BridgeConfig.bridgeAssist;
            button.setMessage(toggleText("Bridge Assist", BridgeConfig.bridgeAssist));
            BridgeConfig.save();
        }).dimensions(left + 15, row1, buttonWidth, 24).build());

        addDrawableChild(ButtonWidget.builder(toggleText("Aim Assist", BridgeConfig.aimAssist), button -> {
            BridgeConfig.aimAssist = !BridgeConfig.aimAssist;
            button.setMessage(toggleText("Aim Assist", BridgeConfig.aimAssist));
            BridgeConfig.save();
        }).dimensions(left + 165, row1, buttonWidth, 24).build());

        addDrawableChild(ButtonWidget.builder(toggleText("Movement Assist", BridgeConfig.movementAssist), button -> {
            BridgeConfig.movementAssist = !BridgeConfig.movementAssist;
            button.setMessage(toggleText("Movement Assist", BridgeConfig.movementAssist));
            BridgeConfig.save();
        }).dimensions(left + 15, row2, buttonWidth, 24).build());

        addDrawableChild(ButtonWidget.builder(toggleText("Edge Warning", BridgeConfig.edgeWarning), button -> {
            BridgeConfig.edgeWarning = !BridgeConfig.edgeWarning;
            button.setMessage(toggleText("Edge Warning", BridgeConfig.edgeWarning));
            BridgeConfig.save();
        }).dimensions(left + 165, row2, buttonWidth, 24).build());

        addDrawableChild(new SliderWidget(left + 15, row3, PANEL_WIDTH - 30, 20,
                Text.literal("Aim strength: " + percent(BridgeConfig.aimStrength)), BridgeConfig.aimStrength) {
            @Override protected void updateMessage() {
                setMessage(Text.literal("Aim strength: " + percent(this.value)));
            }
            @Override protected void applyValue() {
                BridgeConfig.aimStrength = this.value;
                BridgeConfig.save();
            }
        });

        addDrawableChild(new SliderWidget(left + 15, row4, PANEL_WIDTH - 30, 20,
                Text.literal("Bridge delay: " + (int) BridgeConfig.bridgeDelayTicks + " ticks"),
                (BridgeConfig.bridgeDelayTicks - 1.0) / 9.0) {
            @Override protected void updateMessage() {
                setMessage(Text.literal("Bridge delay: " + (1 + (int) Math.round(this.value * 9)) + " ticks"));
            }
            @Override protected void applyValue() {
                BridgeConfig.bridgeDelayTicks = 1.0 + this.value * 9.0;
                BridgeConfig.save();
            }
        });

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset settings"), button -> {
            BridgeConfig.bridgeAssist = false;
            BridgeConfig.aimAssist = false;
            BridgeConfig.movementAssist = false;
            BridgeConfig.edgeWarning = true;
            BridgeConfig.showHud = true;
            BridgeConfig.aimStrength = 0.35;
            BridgeConfig.bridgeDelayTicks = 2.0;
            BridgeConfig.save();
            clearAndInit();
        }).dimensions(left + 15, top + 190, buttonWidth, 24).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left + 165, top + 190, buttonWidth, 24).build());
    }

    private static Text toggleText(String name, boolean enabled) {
        return Text.literal(name + ": " + (enabled ? "ON" : "OFF"));
    }
    private static String percent(double value) { return (int) Math.round(value * 100) + "%"; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0181A27);
        context.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF8B5CF6);
        context.drawCenteredTextWithShadow(this.textRenderer, "AUTO-BRIDGE", this.width / 2, top + 15, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "Minecraft 1.21.11 • Client settings", this.width / 2, top + 32, 0xFFB8B8D0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
