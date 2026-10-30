package dev.autobridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
                BridgeConfig.movementAssist = false;
                BridgeConfig.save();
                notifyPlayer(client, "Panic: all assistance disabled");
            }
            BridgeController.tick(client);
        });
    }

    private static void notifyPlayer(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("[Auto-Bridge] " + message), true);
        }
    }
}
