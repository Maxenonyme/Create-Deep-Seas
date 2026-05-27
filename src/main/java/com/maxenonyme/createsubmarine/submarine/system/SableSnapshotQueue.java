package com.maxenonyme.createsubmarine.submarine.system;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SableSnapshotQueue {

    private static final long TTL_NS = 2_000_000_000L;

    private record Snap(int tick, Pose3d pose, PacketReceiveMode mode, long ts) {}

    private static final Map<Long, Deque<Snap>> PENDING = new ConcurrentHashMap<>();

    public static void enqueue(long plot, int tick, Pose3d pose, PacketReceiveMode mode) {
        Deque<Snap> q = PENDING.computeIfAbsent(plot, k -> new ArrayDeque<>());
        synchronized (q) {
            if (q.size() >= 16) q.pollFirst();
            q.offerLast(new Snap(tick, new Pose3d(pose), mode, System.nanoTime()));
        }
    }

    public static void drain(long plot, ClientSubLevelContainer container, ClientSubLevel sub) {
        Deque<Snap> q = PENDING.remove(plot);
        if (q == null) return;
        long now = System.nanoTime();
        synchronized (q) {
            for (Snap s : q) {
                if (now - s.ts > TTL_NS) continue;
                container.getInterpolation().receiveSnapshot(sub, s.tick, s.pose, s.mode);
            }
        }
    }
}
