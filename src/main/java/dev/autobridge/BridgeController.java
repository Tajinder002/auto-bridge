package dev.autobridge;

import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class BridgeController {
    private static int warningCooldown;
    private static int placementCooldown;
    private static int noBlockWarningCooldown;
    private static int recoveryMessageCooldown;
    private static long sessionEdgeWarnings;
    private static long sessionPlacementAttempts;
    private static long sessionPlacementAccepted;
    private static long sessionPlacementRejected;

    private BridgeController() {}

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null || client.interactionManager == null || client.currentScreen != null) return;

        if (warningCooldown > 0) warningCooldown--;
        if (placementCooldown > 0) placementCooldown--;
        if (noBlockWarningCooldown > 0) noBlockWarningCooldown--;
        if (recoveryMessageCooldown > 0) recoveryMessageCooldown--;

        Direction forward = player.getHorizontalFacing();
        double speed = player.getVelocity().horizontalLength();

        // Predict a missing floor block ahead, with a longer lead while moving quickly.
        if (BridgeConfig.edgeWarning && player.isOnGround()) {
            double lookAhead = speed > 0.22 ? 0.85 : speed > 0.12 ? 0.60 : 0.38;
            Vec3d look = new Vec3d(forward.getOffsetX(), 0, forward.getOffsetZ());
            BlockPos aheadFoot = BlockPos.ofFloored(
                    player.getX() + look.x * lookAhead,
                    player.getY() - 0.15,
                    player.getZ() + look.z * lookAhead);
            boolean unsupported = world.getBlockState(aheadFoot).isAir()
                    || world.getBlockState(aheadFoot).isReplaceable();

            if (unsupported && speed > 0.04 && !player.isSneaking() && warningCooldown == 0) {
                sessionEdgeWarnings++;
                BridgeConfig.totalEdgeWarnings++;
                warningCooldown = 30;
                saveStatsIfEnabled();
                if (BridgeConfig.showHud) {
                    player.sendMessage(Text.literal(
                            "§bAuto-Bridge §7• §eEdge ahead — sneak or stop to avoid falling."), true);
                }
                // Emergency recovery: reduce momentum near a detected edge while bridging.
                if (BridgeConfig.bridgeAssist && client.options.forwardKey.isPressed()) {
                    Vec3d velocity = player.getVelocity();
                    player.setVelocity(velocity.x * 0.35, velocity.y, velocity.z * 0.35);
                    if (recoveryMessageCooldown == 0 && BridgeConfig.showHud) {
                        player.sendMessage(Text.literal(
                                "§bAuto-Bridge §7• §cEmergency recovery: slowing near edge."), true);
                        recoveryMessageCooldown = 40;
                    }
                }
            }
        }

        if (BridgeConfig.bridgeAssist && BridgeConfig.movementAssist
                && player.isOnGround() && player.isSneaking()) {
            Vec3d velocity = player.getVelocity();
            if (velocity.horizontalLengthSquared() > 0.0001) {
                double damping = "SAFE".equals(BridgeConfig.normalizeMode(BridgeConfig.bridgeMode)) ? 0.78 : 0.90;
                player.setVelocity(velocity.x * damping, velocity.y, velocity.z * damping);
            }
        }

        if (BridgeConfig.bridgeAssist
                && client.options.forwardKey.isPressed() && placementCooldown == 0
                && speed > 0.03) {
            tryPlaceBridgeBlock(client, player, world, forward, speed);
        }
    }

    private static void tryPlaceBridgeBlock(MinecraftClient client, ClientPlayerEntity player,
                                            World world, Direction forward, double speed) {
        BlockPos support = findSupport(world, player, forward);
        if (support == null) {
            placementCooldown = 1;
            return;
        }

        BlockPos target = support.offset(forward);
        if (!world.getBlockState(target).isReplaceable()) {
            placementCooldown = 1;
            return;
        }

        ItemStack stack = player.getMainHandStack();
        if (!isUsefulBridgeBlock(stack, world)) {
            if (!selectHotbarBlock(player, world)) {
                placementCooldown = 2;
                if (BridgeConfig.showHud && noBlockWarningCooldown == 0) {
                    player.sendMessage(Text.literal("§bAuto-Bridge §7• §cNo safe bridge blocks in hotbar."), true);
                    noBlockWarningCooldown = 60;
                }
                return;
            }
            stack = player.getMainHandStack();
        }
        if (!isUsefulBridgeBlock(stack, world)) {
            placementCooldown = 2;
            if (BridgeConfig.showHud) {
                player.sendMessage(Text.literal("§bAuto-Bridge §7• §cNo safe bridge blocks selected."), true);
            }
            return;
        }

        Vec3d hitPos = Vec3d.ofCenter(support).add(
                forward.getOffsetX() * 0.5, 0.0, forward.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, forward, support, false);
        sessionPlacementAttempts++;
        BridgeConfig.totalPlacementAttempts++;
        saveStatsIfEnabled();
        net.minecraft.util.ActionResult result =
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            sessionPlacementAccepted++;
            BridgeConfig.totalPlacementAccepted++;
            saveStatsIfEnabled();
            double factor = switch (BridgeConfig.normalizeMode(BridgeConfig.bridgeMode)) {
                case "SAFE" -> 1.35;
                case "FAST" -> 0.55;
                default -> {
                    double speedFactor = speed > 0.22 ? 0.65 : speed > 0.12 ? 0.8 : 1.0;
                    // Simple online adaptation: slow down briefly after repeated rejected
                    // placements, and retain normal timing when placements are succeeding.
                    double rejectionRate = sessionPlacementAttempts == 0 ? 0.0
                            : (double) sessionPlacementRejected / sessionPlacementAttempts;
                    double learningFactor = rejectionRate > 0.35 ? 1.25 : 1.0;
                    yield speedFactor * learningFactor;
                }
            };
            placementCooldown = Math.max(1, (int) Math.round(BridgeConfig.bridgeDelayTicks * factor));
        } else {
            sessionPlacementRejected++;
            BridgeConfig.totalPlacementRejected++;
            saveStatsIfEnabled();
            // Recover from a rejected placement by reducing forward momentum and
            // allowing a short retry window instead of continuing at full speed.
            Vec3d velocity = player.getVelocity();
            player.setVelocity(velocity.x * 0.45, velocity.y, velocity.z * 0.45);
            placementCooldown = 3;
            if (BridgeConfig.showHud && recoveryMessageCooldown == 0) {
                player.sendMessage(Text.literal(
                        "§bAuto-Bridge §7• §ePlacement failed — slowing down to recover."), true);
                recoveryMessageCooldown = 40;
            }
        }
    }

    // Smart placement search: inspect nearby support blocks and score them by
    // forward alignment, distance, and whether the target cell is safely replaceable.
    private static BlockPos findSupport(World world, ClientPlayerEntity player, Direction forward) {
        BlockPos feet = BlockPos.ofFloored(player.getX(), player.getY() - 0.05, player.getZ());
        Direction side = forward.rotateYClockwise();
        Direction otherSide = forward.rotateYCounterclockwise();
        BlockPos[] candidates = {
                feet,
                feet.down(),
                feet.offset(forward.getOpposite()),
                feet.down().offset(forward.getOpposite()),
                feet.offset(side),
                feet.down().offset(side),
                feet.offset(otherSide),
                feet.down().offset(otherSide),
                feet.offset(forward, -2),
                feet.down().offset(forward, -2)
        };

        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (BlockPos candidate : candidates) {
            if (!isSolidSupport(world, candidate)) continue;

            BlockPos target = candidate.offset(forward);
            if (!world.getBlockState(target).isReplaceable()) continue;
            // Avoid attempting a placement when the target is occupied by a fluid.
            if (!world.getFluidState(target).isEmpty()) continue;

            double dx = (candidate.getX() + 0.5) - player.getX();
            double dz = (candidate.getZ() + 0.5) - player.getZ();
            double distanceScore = dx * dx + dz * dz;
            double forwardScore = candidate.getX() * forward.getOffsetX()
                    + candidate.getZ() * forward.getOffsetZ();
            double score = distanceScore - forwardScore * 0.12
                    + (candidate.getY() < feet.getY() ? 0.15 : 0.0);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isSolidSupport(World world, BlockPos pos) {
        return !world.getBlockState(pos).isAir()
                && world.getBlockState(pos).isSolidBlock(world, pos);
    }

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

    private static void saveStatsIfEnabled() {
        if (BridgeConfig.persistentStats) BridgeConfig.save();
    }

    public static long getSessionEdgeWarnings() { return sessionEdgeWarnings; }
    public static long getSessionPlacementAttempts() { return sessionPlacementAttempts; }
    public static long getSessionPlacementAccepted() { return sessionPlacementAccepted; }
    public static long getSessionPlacementRejected() { return sessionPlacementRejected; }

    public static void resetSessionStats() {
        sessionEdgeWarnings = 0;
        sessionPlacementAttempts = 0;
        sessionPlacementAccepted = 0;
        sessionPlacementRejected = 0;
    }

    public static void resetAllStats() {
        resetSessionStats();
        BridgeConfig.totalEdgeWarnings = 0;
        BridgeConfig.totalPlacementAttempts = 0;
        BridgeConfig.totalPlacementAccepted = 0;
        BridgeConfig.totalPlacementRejected = 0;
        BridgeConfig.save();
    }
}
