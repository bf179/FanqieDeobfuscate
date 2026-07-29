package io.github.qauxv.step;

/**
 * Minimal stub for Step interface.
 */
public interface Step {
    boolean step();
    boolean isDone();
    int getPriority();
    String getDescription();
}
