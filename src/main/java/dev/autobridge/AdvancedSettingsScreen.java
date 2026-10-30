package dev.autobridge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class AdvancedSettingsScreen extends Screen {
    private final Screen parent;
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;

    public AdvancedSettingsScreen(Screen parent) {
        super(Text.literal("Auto-Bridge Advanced Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        addDrawableChild(new SliderWidget(left + 20, top + 48, PANEL_WIDTH - 40, 20,
                Text.literal("Target range: " + String.format(java.util.Locale.ROOT, "%.1f", BridgeConfig.aimRange) + " blocks"),
                (BridgeConfig.aimRange - 2.0) / 6.0) {
            @Override protected void updateMessage() {
                double range = 2.0 + this.value * 6.0;
                setMessage(Text.literal("Target range: " + String.format(java.util.Locale.ROOT, "%.1f", range) + " blocks"));
            }
            @Override protected void applyValue() {
                BridgeConfig.aimRange = 2.0 + this.value * 6.0;
                BridgeConfig.save();
            }
        });
        addDrawableChild(new SliderWidget(left + 20, top + 82, PANEL_WIDTH - 40, 20,
                Text.literal("Aim angle: " + (int) BridgeConfig.aimAngle + "°"),
                (BridgeConfig.aimAngle - 10.0) / 35.0) {
            @Override protected void updateMessage() {
                setMessage(Text.literal("Aim angle: " + (10 + (int) Math.round(this.value * 35)) + "°"));
            }
            @Override protected void applyValue() {
                BridgeConfig.aimAngle = 10.0 + this.value * 35.0;
                BridgeConfig.save();
            }
        });
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Auto-recover blocks from inventory: " + (BridgeConfig.autoRecoverBlocks ? "ON" : "OFF")),
                button -> {
                    BridgeConfig.autoRecoverBlocks = !BridgeConfig.autoRecoverBlocks;
                    button.setMessage(Text.literal("Auto-recover blocks from inventory: "
                            + (BridgeConfig.autoRecoverBlocks ? "ON" : "OFF")));
                    BridgeConfig.save();
                }).dimensions(left + 20, top + 112, PANEL_WIDTH - 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset advanced settings"), button -> {
            BridgeConfig.aimRange = 5.0;
            BridgeConfig.aimAngle = 28.0;
            BridgeConfig.autoRecoverBlocks = true;
            BridgeConfig.save();
            if (client != null) client.setScreen(new AdvancedSettingsScreen(parent));
        }).dimensions(left + 20, top + 142, PANEL_WIDTH - 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
            if (client != null) client.setScreen(parent);
        }).dimensions(left + 20, top + 172, PANEL_WIDTH - 40, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101018);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0181A27);
        context.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF8B5CF6);
        context.drawCenteredTextWithShadow(textRenderer, "ADVANCED SETTINGS", width / 2, top + 16, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Practice aim configuration", width / 2, top + 31, 0xFFB8B8D0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
