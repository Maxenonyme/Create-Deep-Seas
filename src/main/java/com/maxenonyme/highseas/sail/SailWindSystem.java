package com.maxenonyme.highseas.sail;

import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.highseas.wind.WindConfig;
import com.maxenonyme.highseas.wind.WindManager;
import com.maxenonyme.highseas.wind.WindSample;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

public final class SailWindSystem {
    private SailWindSystem() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().getTickCount();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            var subs = container.getAllSubLevels();
            for (ServerSubLevel ship : subs) {
                SubLevel root = BoatClassifier.rootOf(level, subs, ship, gameTime);
                if (root == null) {
                    continue;
                }
                applyWind(level, ship, root, gameTime);
            }
        }
    }

    private static void applyWind(ServerLevel parentLevel, ServerSubLevel sailSource, SubLevel root, long gameTime) {
        if (sailSource.getPlot() == null || root.getPlot() == null) {
            return;
        }
        List<SailGroup> sails = SailWindRegistry.getSails(sailSource, gameTime);
        if (sails.isEmpty()) {
            return;
        }

        Pose3dc sailPose = sailSource.logicalPose();
        Quaterniondc sailOrient = sailPose.orientation();

        Pose3dc rootPose = root.logicalPose();
        Quaterniondc rootOrient = rootPose.orientation();
        BoundingBox3ic rbb = root.getPlot().getBoundingBox();
        List<SailGroup> rootSails = SailWindRegistry.getSails(root, gameTime);
        Vec3 rudder = SailWindRegistry.getRudder(root.getUniqueId());
        Vec3 center = new Vec3((rbb.minX() + rbb.maxX()) * 0.5, (rbb.minY() + rbb.maxY()) * 0.5, (rbb.minZ() + rbb.maxZ()) * 0.5);
        Vector3d forward = SailForce.forward(rootOrient, rudder, center, rbb.maxX() - rbb.minX(), rbb.maxZ() - rbb.minZ(), rootSails);
        if (forward == null) {
            return;
        }

        Object handle = SablePhysicsHelper.getHandle(root);
        if (handle == null) {
            return;
        }
        Vector3dc velocity = SablePhysicsHelper.getVelocity(handle);
        double forwardSpeed = 0.0;
        if (velocity != null) {
            forwardSpeed = velocity.x() * forward.x + velocity.y() * forward.y + velocity.z() * forward.z;
        }
        double speedRatio = Mth.clamp(forwardSpeed / WindConfig.SAIL_MAX_SPEED, 0.0, 1.0);

        double power = 0;
        for (SailGroup group : sails) {
            if (group.axis() == Direction.Axis.Y) {
                continue;
            }
            if (!BoatClassifier.inAir(parentLevel, sailSource, group.localCenter())) {
                continue;
            }
            Vector3d worldCenter = new Vector3d(group.localCenter().x, group.localCenter().y, group.localCenter().z);
            sailPose.transformPosition(worldCenter);

            Vec3 ln = group.localNormal();
            Vector3d worldNormal = new Vector3d(ln.x, ln.y, ln.z);
            sailOrient.transform(worldNormal);
            if (worldNormal.lengthSquared() < 1.0e-9) {
                continue;
            }
            worldNormal.normalize();

            WindSample wind = WindManager.getWind(parentLevel, worldCenter.x, worldCenter.y, worldCenter.z);
            double sailPower = SailForce.power(wind.vector(), worldNormal.x, worldNormal.y, worldNormal.z,
                    forward.x, forward.y, forward.z, group.area());
            
            long age = gameTime - group.startTick();
            double factor = Math.min(1.0, age / 60.0);
            
            power += sailPower * factor;

            if (sailPower > 0.1 && speedRatio > 0.05 && parentLevel.random.nextFloat() < (0.4f * speedRatio)) {
                org.joml.Vector3f dir = new org.joml.Vector3f((float) wind.vector().x, (float) wind.vector().y, (float) wind.vector().z);
                if (dir.lengthSquared() > 1.0e-6f) {
                    dir.normalize();
                    org.joml.Quaternionf particleOrientation = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0.0f, 1.0f, 0.0f), dir);
                    int particleCount = (int) Math.ceil(4 * speedRatio);
                    parentLevel.sendParticles(new dev.eriksonn.aeronautics.content.particle.GustParticleData(particleOrientation), worldCenter.x, worldCenter.y, worldCenter.z, particleCount, 3.0, 3.0, 3.0, 0.0);
                }
            }
        }

        List<DecayingSail> decaying = SailWindRegistry.getDecayingSails(sailSource.getUniqueId(), gameTime);
        for (DecayingSail ds : decaying) {
            SailGroup group = ds.group();
            if (group.axis() == Direction.Axis.Y) {
                continue;
            }
            if (!BoatClassifier.inAir(parentLevel, sailSource, group.localCenter())) {
                continue;
            }
            Vector3d worldCenter = new Vector3d(group.localCenter().x, group.localCenter().y, group.localCenter().z);
            sailPose.transformPosition(worldCenter);

            Vec3 ln = group.localNormal();
            Vector3d worldNormal = new Vector3d(ln.x, ln.y, ln.z);
            sailOrient.transform(worldNormal);
            if (worldNormal.lengthSquared() < 1.0e-9) {
                continue;
            }
            worldNormal.normalize();

            WindSample wind = WindManager.getWind(parentLevel, worldCenter.x, worldCenter.y, worldCenter.z);
            double sailPower = SailForce.power(wind.vector(), worldNormal.x, worldNormal.y, worldNormal.z,
                    forward.x, forward.y, forward.z, group.area());

            long age = gameTime - ds.startTick();
            double factor = Math.max(0.0, (60.0 - age) / 60.0);
            power += sailPower * factor;
        }

        if (power < 1.0e-9) {
            return;
        }

        double total = power * WindConfig.SAIL_FORCE_K;

        if (velocity != null) {
            double speedFactor = Mth.clamp((WindConfig.SAIL_MAX_SPEED - forwardSpeed) / WindConfig.SAIL_SPEED_RAMP, 0.0, 1.0);
            total *= speedFactor;
        }
        if (total < 1.0e-9) {
            return;
        }

        Vector3d forceWorld = new Vector3d(forward.x * total, 0.0, forward.z * total);
        rootOrient.conjugate(new Quaterniond()).transform(forceWorld);
        SablePhysicsHelper.applyLinearImpulse(handle, forceWorld);
    }
}
