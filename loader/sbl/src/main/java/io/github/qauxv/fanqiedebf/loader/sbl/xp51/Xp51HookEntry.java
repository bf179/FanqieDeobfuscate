package io.github.qauxv.fanqiedebf.loader.sbl.xp51;

import androidx.annotation.Keep;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.qauxv.fanqiedebf.loader.sbl.common.ModuleLoader;
import io.github.qauxv.fanqiedebf.loader.sbl.common.WellKnownConstants;

/**
 * Entry point for started Xposed API 51-99.
 * <p>
 * Xposed is used as ART hook implementation.
 */
@Keep
public class Xp51HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static XC_LoadPackage.LoadPackageParam sLoadPackageParam = null;
    private static IXposedHookZygoteInit.StartupParam sInitZygoteStartupParam = null;
    private static String sModulePath = null;

    public static String sCurrentPackageName = null;

    /**
     * *** No kotlin code should be invoked here.*** May cause a crash.
     */
    @Keep
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws ReflectiveOperationException {
        android.util.Log.i("FanqieDebug", "[Xp51HookEntry] handleLoadPackage called, packageName=" + lpparam.packageName
                + ", processName=" + (lpparam.processName != null ? lpparam.processName : "null"));
        sLoadPackageParam = lpparam;
        // check LSPosed dex-obfuscation
        Class<?> kXposedBridge = XposedBridge.class;
        android.util.Log.i("FanqieDebug", "[Xp51HookEntry] XposedBridge class loader: " + kXposedBridge.getClassLoader()
                + ", PACKAGE_NAME_SELF=" + WellKnownConstants.PACKAGE_NAME_SELF);
        switch (lpparam.packageName) {
            case WellKnownConstants.PACKAGE_NAME_SELF: {
                android.util.Log.i("FanqieDebug", "[Xp51HookEntry] matched SELF package, calling Xp51HookStatusInit.init");
                try {
                    Xp51HookStatusInit.init(lpparam.classLoader);
                    android.util.Log.i("FanqieDebug", "[Xp51HookEntry] Xp51HookStatusInit.init SUCCESS");
                } catch (Throwable t) {
                    android.util.Log.e("FanqieDebug", "[Xp51HookEntry] Xp51HookStatusInit.init FAILED: " + t, t);
                    throw t;
                }
                break;
            }
            case WellKnownConstants.PACKAGE_NAME_TIM:
            case WellKnownConstants.PACKAGE_NAME_QQ:
            case WellKnownConstants.PACKAGE_NAME_QQ_HD:
            case WellKnownConstants.PACKAGE_NAME_QQ_LITE: {
                android.util.Log.i("FanqieDebug", "[Xp51HookEntry] matched HOST package=" + lpparam.packageName
                        + ", modulePath=" + (sModulePath != null ? sModulePath : "null")
                        + ", appInfo.dataDir=" + lpparam.appInfo.dataDir);
                if (sInitZygoteStartupParam == null) {
                    android.util.Log.e("FanqieDebug", "[Xp51HookEntry] sInitZygoteStartupParam is null, initZygote may not have been called!");
                    throw new IllegalStateException("handleLoadPackage: sInitZygoteStartupParam is null");
                }
                sCurrentPackageName = lpparam.packageName;
                try {
                    android.util.Log.i("FanqieDebug", "[Xp51HookEntry] calling ModuleLoader.initialize...");
                    ModuleLoader.initialize(lpparam.appInfo.dataDir, lpparam.classLoader,
                            Xp51HookImpl.INSTANCE, Xp51HookImpl.INSTANCE, getModulePath(), true);
                    android.util.Log.i("FanqieDebug", "[Xp51HookEntry] ModuleLoader.initialize SUCCESS");
                } catch (Throwable t) {
                    android.util.Log.e("FanqieDebug", "[Xp51HookEntry] ModuleLoader.initialize FAILED: " + t, t);
                    throw t;
                }
                break;
            }
            case WellKnownConstants.PACKAGE_NAME_QQ_INTERNATIONAL: {
                //coming...
                break;
            }
            default:
                android.util.Log.i("FanqieDebug", "[Xp51HookEntry] packageName not in target list, skip: " + lpparam.packageName);
                break;
        }
    }

    /**
     * *** No kotlin code should be invoked here.*** May cause a crash.
     */
    @Override
    public void initZygote(StartupParam startupParam) {
        sInitZygoteStartupParam = startupParam;
        sModulePath = startupParam.modulePath;
    }

    /**
     * Get the {@link XC_LoadPackage.LoadPackageParam} of the current module.
     *
     * @return the lpparam
     */
    public static XC_LoadPackage.LoadPackageParam getLoadPackageParam() {
        if (sLoadPackageParam == null) {
            throw new IllegalStateException("LoadPackageParam is null");
        }
        return sLoadPackageParam;
    }

    /**
     * Get the path of the current module.
     *
     * @return the module path
     */
    public static String getModulePath() {
        if (sModulePath == null) {
            throw new IllegalStateException("Module path is null");
        }
        return sModulePath;
    }

    /**
     * Get the {@link IXposedHookZygoteInit.StartupParam} of the current module.
     *
     * @return the initZygote param
     */
    public static IXposedHookZygoteInit.StartupParam getInitZygoteStartupParam() {
        if (sInitZygoteStartupParam == null) {
            throw new IllegalStateException("InitZygoteStartupParam is null");
        }
        return sInitZygoteStartupParam;
    }

}
