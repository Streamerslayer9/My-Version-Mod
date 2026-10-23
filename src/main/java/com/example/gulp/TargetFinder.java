package com.example.gulp;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Forgiving "what is the player looking at" check, shared by client and server.
 * 1) Every mob's hitbox is made a bit bigger, so clipping any part of it (or just past its edge) counts.
 * 2) If nothing is hit directly, the mob closest to the center of the crosshair (within a small cone) is used,
 *    so aiming at its feet or the ground right beside it still works.
 */
public final class TargetFinder {
    public static final double REACH = 4.0;
    /** How much bigger every mob's hitbox is, in blocks, on every side. */
    private static final double GRACE = 0.3;
    /** Aim assist cone: about 12 degrees around the crosshair. */
    private static final double ASSIST_COS = Math.cos(Math.toRadians(12));

    private TargetFinder() { }

    public static boolean valid(Entity e) {
        if (e instanceof ItemEntity) return e.isAlive(); // dropped items go into item storage
        // Mobs and other players (the server decides whether a player may actually be swallowed).
        return e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
                && !(e instanceof EnderDragon) && !(e instanceof WitherBoss);
    }

    public static Entity find(Player p, double reach) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(reach));
        AABB area = p.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.5);

        Entity direct = null;
        double directDist = Double.MAX_VALUE;
        Entity assist = null;
        double bestDot = ASSIST_COS;

        for (Entity e : p.level().getEntities(p, area, TargetFinder::valid)) {
            AABB box = e.getBoundingBox().inflate(GRACE);
            if (box.contains(eye)) return e; // you're inside it
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < directDist) {
                    directDist = d;
                    direct = e;
                }
            } else {
                Vec3 toCenter = e.getBoundingBox().getCenter().subtract(eye);
                double len = toCenter.length();
                if (len > 0.001 && len <= reach + 1.0) {
                    double dot = toCenter.scale(1.0 / len).dot(look);
                    if (dot > bestDot) {
                        bestDot = dot;
                        assist = e;
                    }
                }
            }
        }
        return direct != null ? direct : assist;
    }

    /** Server-side sanity check for a target the client says it was looking at. */
    public static boolean withinReach(Player p, Entity e, double slack) {
        Vec3 eye = p.getEyePosition();
        AABB b = e.getBoundingBox();
        double x = Mth.clamp(eye.x, b.minX, b.maxX);
        double y = Mth.clamp(eye.y, b.minY, b.maxY);
        double z = Mth.clamp(eye.z, b.minZ, b.maxZ);
        double max = REACH + slack;
        return eye.distanceToSqr(x, y, z) <= max * max;
    }
}
