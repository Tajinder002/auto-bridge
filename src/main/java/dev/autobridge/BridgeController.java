package dev.autobridge;

import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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

        if (BridgeConfig.silentAimPractice) applySilentAimPractice(client, player);
        if (BridgeConfig.aimAssist) applyPracticeAimAssist(client, player);

        Direction forward = player.getHorizontalFacing();
        double speed = player.getVelocity().horizontalLength();

        // Predict a missing floor block ahead, with a longer lead while moving quickly.
        if (BridgeConfig.edgeWarning && player.isOnGround()) {
            String mode = BridgeConfig.normalizeMode(BridgeConfig.bridgeMode);
            double lookAhead = switch (mode) {
                case "SAFE" -> speed > 0.22 ? 1.00 : speed > 0.12 ? 0.72 : 0.48;
                case "PRECISE" -> speed > 0.22 ? 0.72 : speed > 0.12 ? 0.52 : 0.34;
                case "AGGRESSIVE", "FAST" -> speed > 0.22 ? 0.95 : speed > 0.12 ? 0.68 : 0.42;
                default -> speed > 0.22 ? 0.85 : speed > 0.12 ? 0.60 : 0.38;
            };
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

        if (BridgeConfig.bridgeAssist && BridgeConfig.movementAssist) {
            applyMovementAssist(client, player, forward, speed);
        }

        if (BridgeConfig.bridgeAssist
                && client.options.forwardKey.isPressed() && placementCooldown == 0
                && speed > 0.03) {
            tryPlaceBridgeBlock(client, player, world, forward, speed);
        }
    }



    private static void applySilentAimPractice(MinecraftClient client, ClientPlayerEntity player) {
        // Local single-player practice only; never changes view rotation or targets remote players.
        if (!client.isInSingleplayer() || !client.options.attackKey.isPressed()
                || client.world == null) return;

        Vec3d eye = player.getEyePos();
        net.minecraft.entity.LivingEntity best = null;
        double bestAngle = 24.0;
        for (net.minecraft.entity.LivingEntity target :
                client.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
                        player.getBoundingBox().expand(BridgeConfig.aimRange),
                        entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
            Vec3d delta = target.getEyePos().subtract(eye);
            double distance = delta.length();
            if (distance < 0.001 || distance > BridgeConfig.aimRange) continue;
            double yaw = Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0;
            double pitch = -Math.toDegrees(Math.atan2(delta.y,
                    Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
            double yawDelta = net.minecraft.util.math.MathHelper.wrapDegrees(yaw - player.getYaw());
            double pitchDelta = net.minecraft.util.math.MathHelper.wrapDegrees(pitch - player.getPitch());
            double angle = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = target;
            }
        }
        if (best != null) {
            client.crosshairTarget = new net.minecraft.util.hit.EntityHitResult(best);
        }
    }

    private static void applyPracticeAimAssist(MinecraftClient client, ClientPlayerEntity player) {
        // Deliberately restricted to single-player practice worlds.
        if (!client.isInSingleplayer() || !client.options.attackKey.isPressed()) return;

        net.minecraft.util.math.Box searchBox = player.getBoundingBox().expand(BridgeConfig.aimRange);
        net.minecraft.entity.LivingEntity bestTarget = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3d eye = player.getEyePos();

        for (net.minecraft.entity.LivingEntity target :
                client.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, searchBox,
                        entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
            Vec3d toTarget = target.getEyePos().subtract(eye);
            double distance = toTarget.length();
            if (distance < 0.001 || distance > BridgeConfig.aimRange) continue;

            double targetYaw = Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0;
            double targetPitch = -Math.toDegrees(Math.atan2(toTarget.y,
                    Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)));
            double yawDelta = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - player.getYaw());
            double pitchDelta = net.minecraft.util.math.MathHelper.wrapDegrees(targetPitch - player.getPitch());
            double angle = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);

            // Keep assistance subtle and require the target to already be near the crosshair.
            if (angle <= BridgeConfig.aimAngle && angle < bestAngle) {
                bestAngle = angle;
                bestTarget = target;
            }
        }

        if (bestTarget == null) return;
        Vec3d direction = bestTarget.getEyePos().subtract(eye);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
        float strength = (float) Math.max(0.05, Math.min(0.35, BridgeConfig.aimStrength * 0.35));
        float yawStep = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - player.getYaw()) * strength;
        float pitchStep = net.minecraft.util.math.MathHelper.wrapDegrees(targetPitch - player.getPitch()) * strength;
        player.setYaw(player.getYaw() + yawStep);
        player.setPitch(Math.max(-90.0F, Math.min(90.0F, player.getPitch() + pitchStep)));
    }

    private static void applyMovementAssist(MinecraftClient client, ClientPlayerEntity player,
                                             Direction forward, double speed) {
        Vec3d velocity = player.getVelocity();
        String mode = BridgeConfig.normalizeMode(BridgeConfig.bridgeMode);

        // Smooth excess sideways drift while bridging, but preserve vertical motion.
        double forwardX = forward.getOffsetX();
        double forwardZ = forward.getOffsetZ();
        double forwardSpeed = velocity.x * forwardX + velocity.z * forwardZ;
        double sideX = velocity.x - forwardX * forwardSpeed;
        double sideZ = velocity.z - forwardZ * forwardSpeed;
        double sideDamping = switch (mode) {
            case "SAFE", "PRECISE" -> 0.55;
            case "AGGRESSIVE", "FAST" -> 0.88;
            default -> 0.72;
        };

        double x = forwardX * forwardSpeed + sideX * sideDamping;
        double z = forwardZ * forwardSpeed + sideZ * sideDamping;

        // Sneaking near an edge should be controlled; normal forward movement remains responsive.
        if (player.isOnGround() && player.isSneaking()) {
            double sneakDamping = switch (mode) {
                case "SAFE", "PRECISE" -> 0.72;
                case "AGGRESSIVE", "FAST" -> 0.94;
                default -> 0.84;
            };
            x *= sneakDamping;
            z *= sneakDamping;
        }

        // Avoid adding movement when the player is not actively moving.
        if (speed > 0.015 && (Math.abs(x - velocity.x) > 0.002 || Math.abs(z - velocity.z) > 0.002)) {
            player.setVelocity(x, velocity.y, z);
        }

        // Briefly brake when the edge detector sees unsupported ground ahead.
        if (BridgeConfig.edgeWarning && BridgeConfig.bridgeAssist && player.isOnGround()
                && !player.isSneaking() && client.options.forwardKey.isPressed() && speed > 0.04) {
            BlockPos ahead = BlockPos.ofFloored(
                    player.getX() + forwardX * (speed > 0.22 ? 0.80 : 0.48),
                    player.getY() - 0.15,
                    player.getZ() + forwardZ * (speed > 0.22 ? 0.80 : 0.48));
            if (client.world != null && (client.world.getBlockState(ahead).isAir()
                    || client.world.getBlockState(ahead).isReplaceable())) {
                Vec3d current = player.getVelocity();
                player.setVelocity(current.x * 0.55, current.y, current.z * 0.55);
            }
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
        boolean heldValuable = !stack.isEmpty()
                && isValuableMaterial(Registries.ITEM.getId(stack.getItem()).getPath());
        if (!isUsefulBridgeBlock(stack, world) || heldValuable) {
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
            if (BridgeConfig.learningEnabled) {
                BridgeConfig.learningAccepted++;
                // Successful placements gradually allow faster timing, with a conservative lower bound.
                BridgeConfig.learnedDelayMultiplier = Math.max(0.65, BridgeConfig.learnedDelayMultiplier * 0.985);
                if (BridgeConfig.persistentStats) BridgeConfig.save();
            }
            double factor = switch (BridgeConfig.normalizeMode(BridgeConfig.bridgeMode)) {
                case "SAFE" -> 1.45;
                case "PRECISE" -> 1.15;
                case "FAST" -> 0.55;
                case "AGGRESSIVE" -> 0.40;
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
            double learningMultiplier = BridgeConfig.learningEnabled ? BridgeConfig.learnedDelayMultiplier : 1.0;
            placementCooldown = Math.max(1, (int) Math.round(BridgeConfig.bridgeDelayTicks * factor * learningMultiplier));
        } else {
            sessionPlacementRejected++;
            BridgeConfig.totalPlacementRejected++;
            if (BridgeConfig.learningEnabled) {
                BridgeConfig.learningRejected++;
                // Rejections increase the delay so the controller becomes more cautious.
                BridgeConfig.learnedDelayMultiplier = Math.min(1.8, BridgeConfig.learnedDelayMultiplier * 1.12);
            }
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
        int bestScore = Integer.MIN_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = player.getInventory().getStack(slot);
            if (!isUsefulBridgeBlock(candidate, world)) continue;

            String itemId = Registries.ITEM.getId(candidate.getItem()).getPath();
            int score = candidate.getCount() * 10;

            // Prefer abundant, ordinary building blocks for bridging.
            if (isPreferredBridgeMaterial(itemId)) score += 1000;
            // Avoid consuming valuable resources when a normal block is available.
            if (isValuableMaterial(itemId)) score -= 2000;

            if (score > bestScore) {
                bestSlot = slot;
                bestScore = score;
            }
        }
        if (bestSlot < 0) return false;
        player.getInventory().setSelectedSlot(bestSlot);
        return true;
    }

    private static boolean isPreferredBridgeMaterial(String itemId) {
        return itemId.equals("cobblestone")
                || itemId.equals("cobbled_deepslate")
                || itemId.equals("stone")
                || itemId.equals("dirt")
                || itemId.equals("coarse_dirt")
                || itemId.equals("netherrack")
                || itemId.equals("blackstone")
                || itemId.equals("andesite")
                || itemId.equals("diorite")
                || itemId.equals("granite")
                || itemId.equals("tuff")
                || itemId.equals("deepslate")
                || itemId.equals("end_stone")
                || itemId.equals("mossy_cobblestone");
    }

    private static boolean isValuableMaterial(String itemId) {
        return itemId.contains("diamond")
                || itemId.contains("emerald")
                || itemId.contains("gold")
                || itemId.contains("iron")
                || itemId.contains("copper")
                || itemId.contains("redstone")
                || itemId.contains("lapis")
                || itemId.contains("amethyst")
                || itemId.contains("ancient_debris")
                || itemId.contains("netherite")
                || itemId.endsWith("_ore")
                || itemId.contains("raw_")
                || itemId.contains("quartz_block")
                || itemId.contains("obsidian")
                || itemId.contains("crying_obsidian")
                || itemId.contains("log")
                || itemId.contains("wood")
                || itemId.contains("planks")
                || itemId.contains("chest");
    }

    private static void saveStatsIfEnabled() {
        if (BridgeConfig.persistentStats) BridgeConfig.save();
    }

    public static boolean isHudSafeBridgeBlock(ItemStack stack, World world) {
        return isUsefulBridgeBlock(stack, world);
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
