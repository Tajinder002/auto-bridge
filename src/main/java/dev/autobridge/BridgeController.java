package dev.autobridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
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

        // Only attempt placement during deliberate forward movement.
        if (BridgeConfig.bridgeAssist
                && client.options.forwardKey.isPressed() && placementCooldown == 0
                && speed > 0.03) {
            tryPlaceBridgeBlock(client, player, world, forward);
        }
    }

    private static void tryPlaceBridgeBlock(MinecraftClient client, ClientPlayerEntity player,
                                            World world, Direction forward) {
        BlockPos support = BlockPos.ofFloored(player.getX(), player.getY() - 0.05, player.getZ());
        if (!isSolidSupport(world, support)) {
            support = support.offset(forward.getOpposite());
        }
        if (!isSolidSupport(world, support)) return;

        BlockPos target = support.offset(forward);
        if (!world.getBlockState(target).isReplaceable()) return;

        ItemStack stack = player.getMainHandStack();
        if (!isUsefulBridgeBlock(stack, world)) {
            if (!selectHotbarBlock(player, world)) return;
            stack = player.getMainHandStack();
        }
        if (!isUsefulBridgeBlock(stack, world)) return;

        Vec3d hitPos = Vec3d.ofCenter(support).add(
                forward.getOffsetX() * 0.5, 0.0, forward.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, forward, support, false);
        net.minecraft.util.ActionResult result =
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            sessionPlacementAttempts++;
            // Adaptive timing: keep the configured delay at low speed, but shorten it
            // slightly while moving faster so placement can keep up with the bridge run.
            double speed = player.getVelocity().horizontalLength();
            double speedFactor = speed > 0.22 ? 0.65 : speed > 0.12 ? 0.8 : 1.0;
            placementCooldown = Math.max(1,
                    (int) Math.round(BridgeConfig.bridgeDelayTicks * speedFactor));
        } else {
            placementCooldown = 1;
        }
    }

    private static boolean isSolidSupport(World world, BlockPos pos) {
        return !world.getBlockState(pos).isAir()
                && world.getBlockState(pos).isSolidBlock(world, pos);
    }

    // Prefer full solid blocks that can safely support the player; avoid gravity blocks
    // such as sand and gravel, and avoid non-solid block items such as torches.
    private static boolean isUsefulBridgeBlock(ItemStack stack, World world) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock) return false;
        return block.getDefaultState().isSolidBlock(world, BlockPos.ORIGIN);
    }

    private static boolean selectHotbarBlock(ClientPlayerEntity player, World world) {
        int bestSlot = -1;
        int bestCount = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = player.getInventory().getStack(slot);
            if (isUsefulBridgeBlock(candidate, world) && candidate.getCount() > bestCount) {
                bestSlot = slot;
                bestCount = candidate.getCount();
            }
        }
        if (bestSlot < 0) return false;
        player.getInventory().setSelectedSlot(bestSlot);
        return true;
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
