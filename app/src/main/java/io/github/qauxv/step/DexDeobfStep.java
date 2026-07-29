package io.github.qauxv.step;

import io.github.qauxv.base.annotation.DexDeobfs;

/**
 * [FanqieDeobfuscate] Minimal stub for DexDeobfStep.
 */
public class DexDeobfStep implements Step {
    private boolean done = false;

    public DexDeobfStep(DexDeobfs dexDeobfs) {
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
