package com.brokenworld.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-side delayed task runner (for sequences like footsteps). */
public final class Scheduler {
    private static final List<Task> TASKS = new ArrayList<>();

    private record Task(int[] ticksLeft, Runnable action) {}

    private Scheduler() {}

    public static void schedule(int delayTicks, Runnable action) {
        TASKS.add(new Task(new int[]{delayTicks}, action));
    }

    public static void tick() {
        if (TASKS.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (--t.ticksLeft()[0] <= 0) {
                due.add(t.action());
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    public static void clear() {
        TASKS.clear();
    }
}
