package dev.autobridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class AutoBridgeClient implements ClientModInitializer {
    private KeyBinding openGuiKey;
    private KeyBinding toggleBridgeKey;
    private KeyBinding panicKey;

    @Override
    public void onInitializeClient() {
        BridgeConfig.load();
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.auto_bridge.open_gui", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, KeyBinding.Category.MISC));
        toggleBridgeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.auto_bridge.toggle_bridge_assist", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B, KeyBinding.Category.MISC));
        panicKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.auto_bridge.panic", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K, KeyBinding.Category.MISC));

        HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(MinecraftClient.getInstance(), context));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new AutoBridgeScreen());
            }
            while (toggleBridgeKey.wasPressed()) {
                BridgeConfig.bridgeAssist = !BridgeConfig.bridgeAssist;
                BridgeConfig.save();
                notifyPlayer(client, "Bridge Assist " + (BridgeConfig.bridgeAssist ? "enabled" : "disabled"));
            }
            while (panicKey.wasPressed()) {
                BridgeConfig.bridgeAssist = false;
                BridgeConfig.aimAssist = false;
                BridgeConfig.silentAimPractice = false;
                BridgeConfig.movementAssist = false;
                BridgeConfig.save();
                notifyPlayer(client, "Panic: all assistance disabled");
            }
            SessionHistory.tick(client);
            BridgeController.tick(client);
        });
    }

    private static void renderHud(MinecraftClient client, DrawContext context) {
        if (!BridgeConfig.showHud || client.player == null || client.world == null
                || client.options.hudHidden || client.currentScreen instanceof AutoBridgeScreen) return;
        int blocks = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem
                    && BridgeController.isHudSafeBridgeBlock(stack, client.world)) blocks += stack.getCount();
        }
        long attempts = BridgeController.getSessionPlacementAttempts();
        long accepted = BridgeController.getSessionPlacementAccepted();
        double rate = attempts == 0 ? 100.0 : accepted * 100.0 / attempts;
        java.util.List<String> hudLines = new java.util.ArrayList<>();
        hudLines.add("AUTO-BRIDGE  •  BEDWARS");
        hudLines.add("Scaffold: " + (BridgeConfig.bridgeAssist ? "ON" : "OFF")
                + "   Mode: " + BridgeConfig.normalizeMode(BridgeConfig.bridgeMode));
        hudLines.add("Blocks: " + blocks + "   Placed: " + accepted + "/" + attempts);
        hudLines.add(String.format(java.util.Locale.ROOT, "Placement success: %.1f%%", rate));
        if (BridgeConfig.bedWarsHud) {
            hudLines.add("XYZ: " + (int) client.player.getX() + " / "
                    + (int) client.player.getY() + " / " + (int) client.player.getZ());
            if (client.getCurrentServerEntry() != null) {
                hudLines.add("Server: " + client.getCurrentServerEntry().address);
            } else {
                hudLines.add("Server: Single-player");
            }
        }
        if (BridgeConfig.targetHud && client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target && target.isAlive()) {
            hudLines.add("TARGET  " + target.getName().getString() + "  "
                    + String.format(java.util.Locale.ROOT, "%.1f HP", target.getHealth()));
        }
        String[] lines = hudLines.toArray(String[]::new);
        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        int x = 8, y = 8, pad = 6, lineHeight = 11;
        context.fill(x, y, x + width + pad * 2 + 2, y + pad * 2 + lineHeight * lines.length, 0xB0101420);
        context.fill(x, y, x + 2, y + pad * 2 + lineHeight * lines.length,
                BridgeConfig.bridgeAssist ? 0xFF36D98A : 0xFF8A929E);
        for (int i = 0; i < lines.length; i++) {
            int color = i == 0 ? 0xFF70D7FF : i == 1
                    ? (BridgeConfig.bridgeAssist ? 0xFF65F0A0 : 0xFFFF7777)
                    : i == 4 ? 0xFFFFD36B : 0xFFFFFFFF;
            context.drawTextWithShadow(client.textRenderer, lines[i], x + pad + 2, y + pad + i * lineHeight, color);
        }
    }

    private static void notifyPlayer(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("[Auto-Bridge] " + message), true);
        }
    }
}
