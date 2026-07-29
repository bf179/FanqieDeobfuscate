package io.github.qauxv.step;

/**
 * Minimal stub for DexDeobfStep.
 * Accepts any target type to satisfy both DexDeobfs and DexKitTarget.
 */
public class DexDeobfStep implements Step {
    private boolean done = false;

    public DexDeobfStep(Object target) {
    }

    @Override
    public boolean step() {
        done = true;
        return true;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getDescription() {
        return "DexDeobfStep";
    }
}
