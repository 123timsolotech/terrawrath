package com.terrawrath.registry;

import java.util.function.Supplier;

/** A late-bound registry holder that works the same on Forge and Fabric. */
public final class Ref<T> implements Supplier<T> {
    private final String name;
    private T value;

    Ref(String name) {
        this.name = name;
    }

    void set(T value) {
        this.value = value;
    }

    public boolean isBound() {
        return value != null;
    }

    @Override
    public T get() {
        if (value == null) {
            throw new IllegalStateException("TerraWrath registry object '" + name + "' used before registration");
        }
        return value;
    }
}
