package com.soulless.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finding what an attack strikes when some mobs are made of many parts (the Leviathan: a small box at its head and a
 * body of hit boxes 30 blocks long). A mob counts as struck when its own box, or any of its parts, is.
 */
public final class Hitboxes {
    /** How far a mob of many parts can reach from its own position. */
    private static final double REACH = 48;

    /**
     * Every living thing in {@code box} that {@code who} accepts and whose own body or one of whose parts passes
     * {@code shape} (tested on the entity itself, then on each part).
     */
    public static List<LivingEntity> struck(Level level, AABB box, Predicate<LivingEntity> who, Predicate<Entity> shape) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, who)) {
            if (shape.test(t) || partStruck(t, box, shape)) out.add(t);
        }
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box.inflate(REACH), e -> e.isMultipartEntity() && who.test(e))) {
            if (!out.contains(t) && partStruck(t, box, shape)) out.add(t);
        }
        return out;
    }

    private static boolean partStruck(LivingEntity t, AABB box, Predicate<Entity> shape) {
        if (!t.isMultipartEntity() || t.getParts() == null) return false;
        for (Entity part : t.getParts()) {
            if (part.getBoundingBox().intersects(box) && shape.test(part)) return true;
        }
        return false;
    }

    private Hitboxes() {}
}
