package com.brokenworld.registry;

/** Something the mod registered; get() gives it (like the registry objects elsewhere in the code). */
public record Registered<T>(T value) {
    public T get() {
        return value;
    }
}
