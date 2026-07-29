package io.github.qauxv.loader.sbl.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dalvik.system.BaseDexClassLoader;
import io.github.qauxv.loader.hookapi.IHookBridge;
import io.github.qauxv.loader.hookapi.ILoaderService;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ModuleLoader {

    private ModuleLoader() {
    }

    private static boolean sLoaded = false;
    private static final ArrayList<Throwable> sInitErrors = new ArrayList<>(1);

    @Nullable
    public static String findTargetModulePath(@NonNull String hostDataDir) {
        // TODO: 2024-07-21 implement this method
        return null;
    }

    public static ClassLoader createTargetClassLoader(@NonNull File path, @NonNull String dataDirPath) {
        ClassLoader parent = new TransitClassLoader();
        File dataDir = new File(dataDirPath);
        if (!dataDir.canWrite()) {
            sInitErrors.add(new IOException("createTargetClassLoader: dataDir is not writable: " + dataDirPath));
            return null;
        }
        // create odex directory if sdk < 26
        File odexDir;
        if (android.os.Build.VERSION.SDK_INT < 26) {
            odexDir = new File(dataDir, "app_odex");
            if (!odexDir.exists() && !odexDir.mkdirs()) {
                sInitErrors.add(new IOException("createTargetClassLoader: failed to create odexDir: " + odexDir));
                return null;
            }
        } else {
            // optimizedDirectory – this parameter is deprecated and has no effect since API level 26.
            odexDir = null;
        }
        // create new class loader
        ClassLoader cl = new BaseDexClassLoader(path.getAbsolutePath(), odexDir, null, parent);
        return cl;
    }

    public static void initialize(
            @NonNull String hostDataDir,
            @NonNull ClassLoader hostClassLoader,
            @NonNull ILoaderService loaderService,
            @Nullable IHookBridge hookBridge,
            @NonNull String selfPath,
            boolean allowDynamicLoad
    ) throws ReflectiveOperationException {
        android.util.Log.i("FanqieDebug", "[ModuleLoader] initialize called, hostDataDir=" + hostDataDir
                + ", selfPath=" + selfPath + ", allowDynamicLoad=" + allowDynamicLoad);
        if (sLoaded) {
            android.util.Log.i("FanqieDebug", "[ModuleLoader] already loaded (sLoaded=true), skip");
            return;
        }
        File targetModule = null;
        boolean useDynamicLoad = false;
        if (allowDynamicLoad) {
            try {
                String path = findTargetModulePath(hostDataDir);
                if (path != null) {
                    targetModule = new File(path);
                }
            } catch (Exception | Error e) {
                sInitErrors.add(e);
                android.util.Log.e("QAuxv", "initialize: findTargetModulePath failed", e);
            }
        }
        if (targetModule != null && targetModule.isFile() && !targetModule.canWrite()) {
            // ART requires W^X since Android 14
            useDynamicLoad = true;
        }
        android.util.Log.i("FanqieDebug", "[ModuleLoader] useDynamicLoad=" + useDynamicLoad
                + ", targetModule=" + (targetModule != null ? targetModule.getAbsolutePath() : "null"));
        ClassLoader targetClassLoader = null;
        try {
            if (useDynamicLoad) {
                targetClassLoader = createTargetClassLoader(targetModule, hostDataDir);
            }
        } catch (Exception | Error e) {
            sInitErrors.add(e);
            android.util.Log.e("QAuxv", "initialize: createTargetClassLoader failed", e);
        }
        // if we failed to create targetClassLoader, fallback to normal startup
        String modulePath;
        if (targetClassLoader == null) {
            targetClassLoader = ModuleLoader.class.getClassLoader();
            modulePath = selfPath;
            android.util.Log.i("FanqieDebug", "[ModuleLoader] fallback to normal startup, classLoader=" + targetClassLoader
                    + ", modulePath=" + modulePath);
        } else {
            modulePath = targetModule.getAbsolutePath();
            android.util.Log.i("FanqieDebug", "[ModuleLoader] using dynamic load, classLoader=" + targetClassLoader
                    + ", modulePath=" + modulePath);
        }
        assert targetClassLoader != null;
        // invoke the startup routine
        android.util.Log.i("FanqieDebug", "[ModuleLoader] loading class io.github.qauxv.startup.UnifiedEntryPoint ...");
        Class<?> kUnifiedEntryPoint;
        try {
            kUnifiedEntryPoint = targetClassLoader.loadClass("io.github.qauxv.startup.UnifiedEntryPoint");
            android.util.Log.i("FanqieDebug", "[ModuleLoader] UnifiedEntryPoint loaded: " + kUnifiedEntryPoint
                    + " (loader=" + kUnifiedEntryPoint.getClassLoader() + ")");
        } catch (Throwable t) {
            android.util.Log.e("FanqieDebug", "[ModuleLoader] Failed to load UnifiedEntryPoint: " + t, t);
            throw t;
        }
        Method initialize;
        try {
            initialize = kUnifiedEntryPoint.getMethod("entry",
                    String.class, String.class, ILoaderService.class, ClassLoader.class, IHookBridge.class);
            android.util.Log.i("FanqieDebug", "[ModuleLoader] entry method found: " + initialize);
        } catch (Throwable t) {
            android.util.Log.e("FanqieDebug", "[ModuleLoader] Failed to find entry method: " + t, t);
            throw t;
        }
        sLoaded = true;
        try {
            android.util.Log.i("FanqieDebug", "[ModuleLoader] invoking UnifiedEntryPoint.entry(...) ...");
            initialize.invoke(null, modulePath, hostDataDir, loaderService, hostClassLoader, hookBridge);
            android.util.Log.i("FanqieDebug", "[ModuleLoader] UnifiedEntryPoint.entry SUCCESS");
        } catch (Throwable t) {
            android.util.Log.e("FanqieDebug", "[ModuleLoader] UnifiedEntryPoint.entry FAILED: " + t, t);
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            android.util.Log.e("FanqieDebug", "[ModuleLoader] root cause: " + cause, cause);
            if (t instanceof ReflectiveOperationException) {
                throw t;
            }
            throw new ReflectiveOperationException(t);
        }
    }

    public static List<Throwable> getInitErrors() {
        return sInitErrors;
    }
}
