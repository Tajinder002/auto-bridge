package dev.autobridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class BridgeController {
    private static int ticks;
    private static int warningCooldown;
    private static boolean wasGrounded;
    private static long sessionEdgeWarnings;

    private BridgeController() {}

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.currentScreen != null) return;

        ticks++;
        if (warningCooldown > 0) warningCooldown--;

        // Check a short distance ahead at the player's foot level, rather than only
        // checking the block directly below the player's current position.
        if (BridgeConfig.edgeWarning && player.isOnGround()) {
            Vec3d look = player.getRotationVec(1.0F);
            double speed = player.getVelocity().horizontalLength();
            double lookAhead = speed > 0.12 ? 0.65 : 0.35;
            double checkX = player.getX() + look.x * lookAhead;
            double checkZ = player.getZ() + look.z * lookAhead;
            BlockPos aheadFoot = BlockPos.ofFloored(checkX, player.getY() - 0.15, checkZ);
            boolean unsupported = client.world.getBlockState(aheadFoot).isAir();

            if (unsupported && speed > 0.04 && !player.isSneaking() && warningCooldown == 0) {
                sessionEdgeWarnings++;
                warningCooldown = 30;
                if (BridgeConfig.showHud) {
                    player.sendMessage(net.minecraft.text.Text.literal(
                            "§bAuto-Bridge §7• §eEdge ahead — sneak or stop to avoid falling."), true);
                }
            }
        }

        // Movement assistance is intentionally conservative: it only reduces
        // horizontal drift while the player is sneaking and Bridge Assist is enabled.
        if (BridgeConfig.bridgeAssist && BridgeConfig.movementAssist
                && player.isOnGround() && player.isSneaking()) {
            Vec3d velocity = player.getVelocity();
            if (velocity.horizontalLengthSquared() > 0.0001) {
                player.setVelocity(velocity.x * 0.90, velocity.y, velocity.z * 0.90);
            }
        }

        wasGrounded = player.isOnGround();
    }

    public static long getSessionEdgeWarnings() {
        return sessionEdgeWarnings;
    }

    public static void resetSessionStats() {
        sessionEdgeWarnings = 0;
    }
}
