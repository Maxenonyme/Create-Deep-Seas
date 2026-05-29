package com.maxenonyme.createsubmarine.submarine.util;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Method;

public class SablePhysicsHelper {

    private static final double DEFAULT_MASS = 1000.0;

    private static final Vector3d ZERO_VEC = new Vector3d(0, 0, 0);

    private static Method ofMethod;
    private static Method isValid;
    private static Method setAsleep;
    private static Method getLinearVelocity;
    private static Method applyLinearImpulse;
    private static Method applyAngularImpulse;
    private static Method addLinearAndAngularVelocity;
    private static Method getMassTracker;
    private static Method getMass;
    private static boolean initialized;

    public static void ensureInit() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> handleClass = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
            for (Method m : handleClass.getMethods()) {
                int p = m.getParameterCount();
                String name = m.getName();
                if (p == 1 && "of".equals(name) && java.lang.reflect.Modifier.isStatic(m.getModifiers())) ofMethod = m;
                else if (p == 0 && "isValid".equals(name)) isValid = m;
                else if (p == 0 && "getLinearVelocity".equals(name) && Vector3dc.class.isAssignableFrom(m.getReturnType())) getLinearVelocity = m;
                else if (p == 1 && "applyLinearImpulse".equals(name)) applyLinearImpulse = m;
                else if (p == 1 && "applyAngularImpulse".equals(name)) applyAngularImpulse = m;
                else if (p == 1 && "setAsleep".equals(name)) setAsleep = m;
                else if (p == 2 && "addLinearAndAngularVelocity".equals(name)) addLinearAndAngularVelocity = m;
            }
        } catch (ClassNotFoundException e) {
        }
    }

    public static Object getHandle(SubLevelAccess sub) {
        ensureInit();
        if (ofMethod == null) return null;
        try {
            Object handle = ofMethod.invoke(null, sub);
            return handle != null && (boolean) isValid.invoke(handle) ? handle : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static Vector3dc getVelocity(SubLevelAccess sub) {
        return getVelocity(getHandle(sub));
    }

    public static Vector3dc getVelocity(Object handle) {
        if (handle == null || getLinearVelocity == null) return null;
        try {
            return (Vector3dc) getLinearVelocity.invoke(handle);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static void wakeUp(Object handle) {
        if (handle == null || setAsleep == null) return;
        try {
            setAsleep.invoke(handle, false);
        } catch (ReflectiveOperationException ignored) {}
    }

    public static void setAsleep(Object handle, boolean asleep) {
        if (handle == null || setAsleep == null) return;
        try {
            setAsleep.invoke(handle, asleep);
        } catch (ReflectiveOperationException ignored) {}
    }

    public static void applyLinearImpulse(Object handle, Vector3d force) {
        if (handle == null || applyLinearImpulse == null) return;
        try {
            applyLinearImpulse.invoke(handle, force);
        } catch (ReflectiveOperationException ignored) {}
    }

    public static void addLinearVelocity(Object handle, Vector3d velocity) {
        if (handle == null || addLinearAndAngularVelocity == null) return;
        try {
            addLinearAndAngularVelocity.invoke(handle, velocity, ZERO_VEC);
        } catch (ReflectiveOperationException ignored) {}
    }

    public static void applyAngularImpulse(Object handle, Vector3d torque) {
        if (handle == null || applyAngularImpulse == null) return;
        try {
            applyAngularImpulse.invoke(handle, torque);
        } catch (ReflectiveOperationException ignored) {}
    }

    public static double readMass(SubLevelAccess sub) {
        try {
            if (getMassTracker == null) {
                getMassTracker = sub.getClass().getMethod("getMassTracker");
            }
            Object tracker = getMassTracker.invoke(sub);
            if (tracker == null) return DEFAULT_MASS;
            if (getMass == null) {
                getMass = tracker.getClass().getMethod("getMass");
            }
            return (double) getMass.invoke(tracker);
        } catch (ReflectiveOperationException e) {
            return DEFAULT_MASS;
        }
    }
}
