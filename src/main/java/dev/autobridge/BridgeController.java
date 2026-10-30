package dev.autobridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class BridgeController {
    private static int warningCooldown;
    private static int placementCooldown;
    private static long sessionEdgeWarnings;
    private static long sessionPlacementAttempts;

    private BridgeController() {}

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null || client.interactionManager == null || client.currentScreen != null) return;

        if (warningCooldown > 0) warningCooldown--;
        if (placementCooldown > 0) placementCooldown--;

        Direction forward = player.getHorizontalFacing();
        double speed = player.getVelocity().horizontalLength();

        if (BridgeConfig.edgeWarning && player.isOnGround()) {
            double lookAhead = speed > 0.12 ? 0.65 : 0.35;
            Vec3d look = player.getRotationVec(1.0F);
            BlockPos aheadFoot = BlockPos.ofFloored(
                    player.getX() + look.x * lookAhead, player.getY() - 0.15,
                    player.getZ() + look.z * lookAhead);
            boolean unsupported = world.getBlockState(aheadFoot).isAir();

            if (unsupported && speed > 0.04 && !player.isSneaking() && warningCooldown == 0) {
                sessionEdgeWarnings++;
                warningCooldown = 30;
                if (BridgeConfig.showHud) {
                    player.sendMessage(net.minecraft.text.Text.literal(
                            "§bAuto-Bridge §7• §eEdge ahead — sneak or stop to avoid falling."), true);
                }
            }
        }

        if (BridgeConfig.bridgeAssist && BridgeConfig.movementAssist
                && player.isOnGround() && player.isSneaking()) {
            Vec3d velocity = player.getVelocity();
            if (velocity.horizontalLengthSquared() > 0.0001) {
                player.setVelocity(velocity.x * 0.90, velocity.y, velocity.z * 0.90);
            }
        }

        // Place only while the player is deliberately moving forward, has a block selected,
        // and a solid support block exists. This uses normal client interaction packets.
        if (BridgeConfig.bridgeAssist && player.isOnGround()
                && client.options.forwardKey.isPressed() && placementCooldown == 0
                && player.getVelocity().horizontalLength() > 0.03) {
            tryPlaceBridgeBlock(client, player, world, forward);
        }
    }

    private static void tryPlaceBridgeBlock(MinecraftClient client, ClientPlayerEntity player,
                                            World world, Direction forward) {
        // Y is the player's feet height. Flooring Y - epsilon gives the
        // block at floor level; stepping down again selected the wrong block.
        BlockPos support = BlockPos.ofFloored(player.getX(), player.getY() - 0.05, player.getZ());
        if (world.getBlockState(support).isAir()
                || !world.getBlockState(support).isSolidBlock(world, support)) {
            support = support.offset(forward.getOpposite());
        }
        if (world.getBlockState(support).isAir()
                || !world.getBlockState(support).isSolidBlock(world, support)) return;

        BlockPos target = support.offset(forward);
        if (!world.getBlockState(target).isAir()) return;

        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem)) {
            if (!selectHotbarBlock(player)) return;
            stack = player.getMainHandStack();
        }
        if (!(stack.getItem() instanceof BlockItem)) return;

        Vec3d hitPos = Vec3d.ofCenter(support).add(
                forward.getOffsetX() * 0.5, 0.0, forward.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, forward, support, false);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        sessionPlacementAttempts++;
        placementCooldown = Math.max(1, (int) Math.round(BridgeConfig.bridgeDelayTicks));
    }

    private static boolean selectHotbarBlock(ClientPlayerEntity player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof BlockItem) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    public static long getSessionEdgeWarnings() {
        return sessionEdgeWarnings;
    }

    public static long getSessionPlacementAttempts() {
        return sessionPlacementAttempts;
    }

    public static void resetSessionStats() {
        sessionEdgeWarnings = 0;
        sessionPlacementAttempts = 0;
    }
}
