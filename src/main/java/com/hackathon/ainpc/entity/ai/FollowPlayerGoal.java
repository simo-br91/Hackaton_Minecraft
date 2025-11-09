package com.hackathon.ainpc.entity.ai;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;

/**
 * Custom AI goal for following a specific player
 */
public class FollowPlayerGoal extends Goal {
    private final PathfinderMob mob;
    private Player targetPlayer;
    private final double speedModifier;
    private final float stopDistance;
    private final float startDistance;
    private int timeToRecalcPath;

    public FollowPlayerGoal(PathfinderMob mob, double speedModifier, float stopDistance, float startDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.stopDistance = stopDistance;
        this.startDistance = startDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Find nearest player
        this.targetPlayer = this.mob.level().getNearestPlayer(this.mob, this.startDistance);
        return this.targetPlayer != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetPlayer == null || !this.targetPlayer.isAlive()) {
            return false;
        }
        
        double distanceSq = this.mob.distanceToSqr(this.targetPlayer);
        return distanceSq <= (this.startDistance * this.startDistance);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.targetPlayer = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null) {
            return;
        }

        // Look at the player
        this.mob.getLookControl().setLookAt(
            this.targetPlayer,
            10.0F,
            (float) this.mob.getMaxHeadXRot()
        );

        double distanceSq = this.mob.distanceToSqr(this.targetPlayer);

        // If too far, move closer
        if (distanceSq > (this.stopDistance * this.stopDistance)) {
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = 10;
                this.mob.getNavigation().moveTo(this.targetPlayer, this.speedModifier);
            }
        } else {
            // Close enough, stop moving
            this.mob.getNavigation().stop();
        }
    }

    /**
     * Set specific player to follow
     */
    public void setTargetPlayer(Player player) {
        this.targetPlayer = player;
    }
}