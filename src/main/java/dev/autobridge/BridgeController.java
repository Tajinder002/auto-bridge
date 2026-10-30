package dev.autobridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class BridgeController {
    private static int ticks;
    private BridgeController() {}

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.currentScreen != null) return;
        ticks++;
        if (BridgeConfig.edgeWarning && player.isOnGround()
                && player.getVelocity().horizontalLengthSquared() > 0.015) {
            BlockPos below = BlockPos.ofFloored(player.getX(), player.getY() - 1.0, player.getZ());
            if (client.world.getBlockState(below).isAir() && BridgeConfig.showHud && ticks % 10 == 0) {
                player.sendMessage(net.minecraft.text.Text.literal(
                        "§bAuto-Bridge §7• §eEdge ahead — sneak or stop to avoid falling."), true);
            }
        }
        // Starter assistance: slight movement damping while sneaking near an edge.
        // It does not issue repeated block-placement packets or bypass server rules.
        if (BridgeConfig.bridgeAssist && player.isOnGround() && player.isSneaking()) {
            Vec3d velocity = player.getVelocity();
            if (velocity.horizontalLengthSquared() > 0.0001) {
                player.setVelocity(velocity.x * 0.98, velocity.y, velocity.z * 0.98);
            }
        }
    }
}
