/*
 * QAuxiliary - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2022 qwq233@qwq2333.top
 * https://github.com/cinit/QAuxiliary
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by QAuxiliary contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/cinit/QAuxiliary/blob/master/LICENSE.md>.
 */
package io.github.qauxv.core;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.system.Os;
import android.system.StructUtsname;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cc.hicore.QApp.QAppUtils;
import cc.ioctl.hook.SettingEntryHook;
import cc.ioctl.hook.bak.MuteAtAllAndRedPacket;
import cc.ioctl.hook.chat.GagInfoDisclosure;
import cc.ioctl.hook.experimental.FileRecvRedirect;
import cc.ioctl.hook.experimental.ForcePadMode;
import cc.ioctl.hook.misc.CustomSplash;
import cc.ioctl.hook.misc.DisableHotPatch;
import cc.ioctl.hook.misc.DisableQQCrashReportManager;
import cc.ioctl.hook.msg.RevokeMsgHook;
import cc.ioctl.hook.notification.MuteQZoneThumbsUp;
import cc.ioctl.hook.ui.misc.OptXListViewScrollBar;
import cc.ioctl.hook.ui.title.RemoveCameraButton;
import cc.ioctl.util.HostInfo;
import cc.ioctl.util.Reflex;
import io.github.qauxv.chainloader.detail.ExternalModuleChainLoader;
import io.github.qauxv.chainloader.detail.ui.ExternalModuleConfigHook;
import io.github.qauxv.util.xpcompat.XC_MethodHook;
import io.github.qauxv.util.xpcompat.XposedBridge;
import io.github.qauxv.config.ConfigItems;
import io.github.qauxv.config.SafeModeManager;
import io.github.qauxv.lifecycle.ActProxyMgr;
import io.github.qauxv.lifecycle.JumpActivityEntryHook;
import io.github.qauxv.lifecycle.Parasitics;
import io.github.qauxv.lifecycle.ShadowFileProvider;
import io.github.qauxv.omnifix.hw.HwResThemeMgrFix;
import io.github.qauxv.util.Initiator;
import io.github.qauxv.util.LicenseStatus;
import io.github.qauxv.util.Log;
import io.github.qauxv.util.SyncUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import xyz.nextalone.hook.RemoveSuperQQShow;

/*TitleKit:Lcom/tencent/mobileqq/widget/navbar/NavBarCommon*/

public class MainHook {

    private static MainHook SELF;

    boolean third_stage_inited = false;

    private MainHook() {
    }

    public static MainHook getInstance() {
        if (SELF == null) {
            SELF = new MainHook();
        }
        return SELF;
    }

    private static void injectLifecycleForProcess(Context ctx) {
        if (SyncUtils.isMainProcess()) {
            Resources res = ctx.getApplicationContext().getResources();
            HwResThemeMgrFix.initHook(ctx);
            HwResThemeMgrFix.fix(ctx, res);
            Parasitics.injectModuleResources(res);
        }
        if (SyncUtils.isTargetProcess(SyncUtils.PROC_MAIN | SyncUtils.PROC_PEAK | SyncUtils.PROC_TOOL)) {
            Parasitics.initForStubActivity(ctx);
        }
        if (SyncUtils.isTargetProcess(SyncUtils.PROC_MAIN | SyncUtils.PROC_TOOL)) {
            try {
                ShadowFileProvider.initHookForFileProvider();
            } catch (ReflectiveOperationException e) {
                Log.e(e);
            }
        }
    }

    public void performHook(@NonNull Context ctx, @Nullable Object step) {
        android.util.Log.i("FanqieDebug", "[MainHook] performHook called, ctx=" + ctx + ", step=" + step);
        SyncUtils.initBroadcast(ctx);
        injectLifecycleForProcess(ctx);
        if (HostInfo.isQQHD()) {
            initForQQHDBasePadActivityMitigation();
        }
        boolean safeMode = SafeModeManager.getManager().isEnabledForNextTime();
        SafeModeManager.getManager().setSafeModeForThisTime(safeMode);
        if (safeMode) {
            LicenseStatus.sDisableCommonHooks = true;
            android.util.Log.i("FanqieDebug", "[MainHook] Safe mode enabled, disable hooks");
        }
        // deliberately allowing DisableHotPatch and DisableQQCrashReportManager in safe mode
        HookInstaller.allowEarlyInit(DisableHotPatch.INSTANCE);
        HookInstaller.allowEarlyInit(DisableQQCrashReportManager.INSTANCE);
        if (!safeMode) {
            HookInstaller.allowEarlyInit(RevokeMsgHook.INSTANCE);
            HookInstaller.allowEarlyInit(MuteQZoneThumbsUp.INSTANCE);
            HookInstaller.allowEarlyInit(MuteAtAllAndRedPacket.INSTANCE);
            HookInstaller.allowEarlyInit(GagInfoDisclosure.INSTANCE);
            HookInstaller.allowEarlyInit(CustomSplash.INSTANCE);
            HookInstaller.allowEarlyInit(RemoveCameraButton.INSTANCE);
            HookInstaller.allowEarlyInit(RemoveSuperQQShow.INSTANCE);
            HookInstaller.allowEarlyInit(FileRecvRedirect.INSTANCE);
            HookInstaller.allowEarlyInit(OptXListViewScrollBar.INSTANCE);
            HookInstaller.allowEarlyInit(ForcePadMode.INSTANCE);
        }
        if (SyncUtils.isMainProcess()) {
            ConfigItems.removePreviousCacheIfNecessary();
            JumpActivityEntryHook.initForJumpActivityEntry(ctx);
            if (!isForegroundStartupForMainProcess(ctx, step) && !safeMode) {
                // since we are in background, we can do some heavy work without compromising user experience
                InjectDelayableHooks.stepForMainBackgroundStartup();
            }
            Class<?> loadData = Initiator.load("com/tencent/mobileqq/startup/step/LoadData");
            if (loadData != null) {
                android.util.Log.i("FanqieDebug", "[MainHook] LoadData class found, hooking doStep for third-stage init");
                Method doStep = null;
                for (Method method : loadData.getDeclaredMethods()) {
                    if (method.getReturnType().equals(boolean.class) && method.getParameterTypes().length == 0) {
                        doStep = method;
                        break;
                    }
                }
                XposedBridge.hookMethod(doStep, new XC_MethodHook(51) {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (third_stage_inited) {
                            android.util.Log.i("FanqieDebug", "[MainHook] LoadData.doStep already inited, skip");
                            return;
                        }
                        android.util.Log.i("FanqieDebug", "[MainHook] LoadData.doStep afterHookedMethod, safeMode=" + safeMode);
                        Object dir = getStartDirector(param.thisObject);
                        if (safeMode) {
                            android.util.Log.i("FanqieDebug", "[MainHook] safeMode -> calling SettingEntryHook.INSTANCE.initialize()");
                            try {
                                SettingEntryHook.INSTANCE.initialize();
                                android.util.Log.i("FanqieDebug", "[MainHook] SettingEntryHook.initialize SUCCESS (safeMode path)");
                            } catch (Throwable t) {
                                android.util.Log.e("FanqieDebug", "[MainHook] SettingEntryHook.initialize FAILED (safeMode): " + t, t);
                                if (t instanceof Error) throw (Error) t;
                                if (t instanceof RuntimeException) throw (RuntimeException) t;
                                throw new RuntimeException(t);
                            }
                        } else {
                            android.util.Log.i("FanqieDebug", "[MainHook] normal mode -> calling InjectDelayableHooks.step(dir)");
                            try {
                                InjectDelayableHooks.step(dir);
                                android.util.Log.i("FanqieDebug", "[MainHook] InjectDelayableHooks.step SUCCESS");
                            } catch (Throwable t) {
                                android.util.Log.e("FanqieDebug", "[MainHook] InjectDelayableHooks.step FAILED: " + t, t);
                                if (t instanceof Error) throw (Error) t;
                                if (t instanceof RuntimeException) throw (RuntimeException) t;
                                throw new RuntimeException(t);
                            }
                        }
                        third_stage_inited = true;
                    }
                });
            } else {
                android.util.Log.i("FanqieDebug", "[MainHook] LoadData not found, running third stage hooks in background");
                if (safeMode) {
                    android.util.Log.i("FanqieDebug", "[MainHook] safeMode bg -> SettingEntryHook.initialize");
                    try {
                        SettingEntryHook.INSTANCE.initialize();
                        android.util.Log.i("FanqieDebug", "[MainHook] SettingEntryHook.initialize SUCCESS (bg safeMode)");
                    } catch (Throwable t) {
                        android.util.Log.e("FanqieDebug", "[MainHook] SettingEntryHook.initialize FAILED (bg safeMode): " + t, t);
                    }
                } else {
                    android.util.Log.i("FanqieDebug", "[MainHook] normal mode bg -> InjectDelayableHooks.step(null)");
                    try {
                        InjectDelayableHooks.step(null);
                        android.util.Log.i("FanqieDebug", "[MainHook] InjectDelayableHooks.step(null) SUCCESS");
                    } catch (Throwable t) {
                        android.util.Log.e("FanqieDebug", "[MainHook] InjectDelayableHooks.step(null) FAILED: " + t, t);
                    }
                }
            }
        } else {
            android.util.Log.i("FanqieDebug", "[MainHook] NOT main process, skip third-stage hooks (safeMode=" + safeMode
                    + ", eulaAccepted=" + LicenseStatus.hasUserAcceptEula() + ")");
            if (!safeMode && LicenseStatus.hasUserAcceptEula()) {
                Object dir = getStartDirector(step);
                InjectDelayableHooks.step(dir);
            }
        }
        // load external modules, if any
        if (!safeMode) {
            try {
                ExternalModuleChainLoader.loadExternalModulesForStartup();
            } catch (IOException | RuntimeException e) {
                ExternalModuleConfigHook.INSTANCE.traceError(e);
            }
        }
        android.util.Log.i("FanqieDebug", "[MainHook] performHook done");
    }

    private static boolean isForegroundStartupForMainProcess(Context ctx, Object step) {
        // TODO: 2022-12-03 find a way to detect foreground startup
        // XXX: BaseApplicationImpl.sIsBgStartup does not work, always false
        // TODO: 2025-06-17 use ActivityManager.getRunningTasks to detect foreground startup
        return false;
    }

    @Nullable
    private static Object getStartDirector(Object step) {
        if (step == null) {
            return null;
        }
        Class<?> director = Initiator._StartupDirector();
        if (director == null && (QAppUtils.isQQnt())) {
            // NT QQ has different StartupDirector, and removed in 8.9.63(4190)
            // TODO: 2023-07-02 handle NT QQ correctly
            return null;
        }
        Object dir = Reflex.getInstanceObjectOrNull(step, "mDirector", director);
        if (dir == null) {
            dir = Reflex.getInstanceObjectOrNull(step, "a", director);
        }
        if (dir == null) {
            dir = Reflex.getFirstNSFByType(step, director);
        }
        return dir;
    }

    private static void initForQQHDBasePadActivityMitigation() {
        Class<?> kBasePadActivity = Initiator.load("mqq.app.BasePadActivity");
        if (kBasePadActivity != null) {
            try {
                Method m = kBasePadActivity.getDeclaredMethod("startActivityForResult", Intent.class, int.class, Bundle.class);
                final Method doStartActivityForResult = kBasePadActivity.getDeclaredMethod("doStartActivityForResult", Intent.class, int.class, Bundle.class);
                doStartActivityForResult.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook(51) {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        Intent intent = (Intent) param.args[0];
                        int requestCode = (int) param.args[1];
                        Bundle options = (Bundle) param.args[2];
                        String className = null;
                        if (intent != null) {
                            ComponentName component = intent.getComponent();
                            if (component != null && HostInfo.getPackageName().equals(component.getPackageName())) {
                                className = component.getClassName();
                            }
                        }
                        if (className == null) {
                            // nothing related to us
                            return;
                        }
                        if (ActProxyMgr.isModuleProxyActivity(className)) {
                            // call original method
                            try {
                                doStartActivityForResult.invoke(activity, intent, requestCode, options);
                                param.setResult(null);
                            } catch (IllegalAccessException e) {
                                throw new AssertionError(e);
                            } catch (InvocationTargetException ite) {
                                Throwable cause = ite.getCause();
                                if (cause != null) {
                                    Log.e("doStartActivityForResult failed: " + cause.getMessage(), cause);
                                } else {
                                    Log.e("doStartActivityForResult failed: " + ite.getMessage(), ite);
                                }
                            }
                        }
                    }
                });
            } catch (NoSuchMethodException e) {
                Log.e("initForQQHDBasePadActivityMitigation: startActivityForResult not found", e);
            }
        }
    }

    public static boolean isWindowsSubsystemForAndroid() {
        StructUtsname uts = Os.uname();
        // XXX: is this reliable?
        return uts.release.contains("-windows-subsystem-for-android-");
    }
}
