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
package cc.ioctl.hook;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static cc.ioctl.util.HostInfo.requireMinQQVersion;
import static io.github.qauxv.util.Initiator.load;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import cc.hicore.QApp.QAppUtils;
import cc.ioctl.util.HookUtils;
import cc.ioctl.util.HostInfo;
import cc.ioctl.util.LayoutHelper;
import cc.ioctl.util.Reflex;
import io.github.qauxv.BuildConfig;
import io.github.qauxv.R;
import io.github.qauxv.activity.SettingsUiFragmentHostActivity;
import io.github.qauxv.base.annotation.FunctionHookEntry;
import io.github.qauxv.core.HookInstaller;
import io.github.qauxv.fragment.EulaFragment;
import io.github.qauxv.fragment.FuncStatusDetailsFragment;
import io.github.qauxv.hook.BasePersistBackgroundHook;
import io.github.qauxv.lifecycle.Parasitics;
import io.github.qauxv.step.Step;
import io.github.qauxv.util.Initiator;
import io.github.qauxv.util.LicenseStatus;
import io.github.qauxv.util.Log;
import io.github.qauxv.util.QQVersion;
import io.github.qauxv.util.dexkit.DexDeobfsProvider;
import io.github.qauxv.util.dexkit.DexKit;
import io.github.qauxv.util.dexkit.DexKitTargetSealedEnum;
import io.github.qauxv.util.dexkit.SimpleItemProcessor_Method;
import io.github.qauxv.util.dexkit.impl.DexKitDeobfs;
import io.github.qauxv.util.xpcompat.XC_MethodHook;
import io.github.qauxv.util.xpcompat.XposedBridge;
import io.github.qauxv.util.xpcompat.XposedHelpers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kotlin.collections.ArraysKt;
import kotlin.jvm.functions.Function0;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

@FunctionHookEntry
public class SettingEntryHook extends BasePersistBackgroundHook {

    public static final SettingEntryHook INSTANCE = new SettingEntryHook();

    private static final int BG_TYPE_SINGLE = 0;
    private static final int BG_TYPE_FIRST = 1;
    private static final int BG_TYPE_MIDDLE = 2;
    private static final int BG_TYPE_LAST = 3;

    // am start "intent:#Intent;component=com.tencent.mobileqq/com.tencent.mobileqq.activity.QPublicFragmentActivity;S.public_fragment_class=com.tencent.mobileqq.setting.main.MainSettingFragment;end"

    private SettingEntryHook() {
    }

    @Override
    public boolean isPreparationRequired() {
        return isNeedFind();
    }

    private final Step mStep = new Step() {
        @Override
        public boolean step() {
            return doFindStep();
        }

        @Override
        public boolean isDone() {
            return !isNeedFind();
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public String getDescription() {
            return "查找设置入口相关类";
        }
    };

    @Override
    public Step[] makePreparationSteps() {
        return new Step[]{mStep};
    }

    private boolean isNeedFind() {
        return QAppUtils.isQQnt()
                && requireMinQQVersion(QQVersion.QQ_9_2_10)
                && DexKit.getMethodDescFromCacheImpl(SimpleItemProcessor_Method.INSTANCE) == null;
    }

    private boolean doFindStep() {
        DexDeobfsProvider.checkDeobfuscationAvailable();
        try (DexKitDeobfs dexKitDeobfs = DexKitDeobfs.newInstance()) {
            DexKitBridge bridge = dexKitDeobfs.getDexKitBridge();
            MethodDataList result = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .addEqString("SimpleItemProcessor")
                    )
            );
            if (result.size() == 1) {
                MethodData methodData = result.get(0);
                SimpleItemProcessor_Method.INSTANCE.setDescCache(methodData.getDescriptor());
                Log.d("save id: " + DexKitTargetSealedEnum.INSTANCE.nameOf(SimpleItemProcessor_Method.INSTANCE) + ",method: " + methodData.getDescriptor());
                return true;
            }
            SimpleItemProcessor_Method.INSTANCE.setDescCache(DexKit.NO_SUCH_METHOD.toString());
            return false;
        }
    }

    @Override
    public boolean initOnce() throws Exception {
        android.util.Log.i("FanqieDebug", "[SettingEntryHook] initOnce() START");
        android.util.Log.i("FanqieDebug", "[SettingEntryHook] entry name to inject: 小番茄解混淆");
        try {
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] calling injectSettingEntryForMainSettingConfigProvider...");
            injectSettingEntryForMainSettingConfigProvider();
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] injectSettingEntryForMainSettingConfigProvider SUCCESS");
        } catch (Throwable t) {
            android.util.Log.e("FanqieDebug", "[SettingEntryHook] injectSettingEntryForMainSettingConfigProvider FAILED: " + t, t);
            throw t;
        }
        // below 8.9.70
        Class<?> kQQSettingSettingActivity = Initiator._QQSettingSettingActivity();
        android.util.Log.i("FanqieDebug", "[SettingEntryHook] kQQSettingSettingActivity=" + kQQSettingSettingActivity);
        if (kQQSettingSettingActivity != null) {
            XposedHelpers.findAndHookMethod(kQQSettingSettingActivity, "doOnCreate", Bundle.class, mAddModuleEntry);
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] hooked QQSettingSettingActivity.doOnCreate");
        }
        Class<?> kQQSettingSettingFragment = Initiator._QQSettingSettingFragment();
        android.util.Log.i("FanqieDebug", "[SettingEntryHook] kQQSettingSettingFragment=" + kQQSettingSettingFragment);
        if (kQQSettingSettingFragment != null) {
            Method doOnCreateView = kQQSettingSettingFragment.getDeclaredMethod("doOnCreateView",
                    LayoutInflater.class, ViewGroup.class, Bundle.class);
            XposedBridge.hookMethod(doOnCreateView, mAddModuleEntry);
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] hooked QQSettingSettingFragment.doOnCreateView");
        }
        android.util.Log.i("FanqieDebug", "[SettingEntryHook] initOnce() DONE");
        return true;
    }

    private void injectSettingEntryForMainSettingConfigProvider() throws ReflectiveOperationException {
        // 8.9.70+
        Class<?> kMainSettingFragment = Initiator.load("com.tencent.mobileqq.setting.main.MainSettingFragment");
        android.util.Log.i("FanqieDebug", "[SettingEntryHook] MainSettingFragment class: " + kMainSettingFragment);
        if (kMainSettingFragment != null) {
            // MainSettingConfigProvider was removed in 9.1.65.24690(9516) gray release
            Class<?> kMainSettingConfigProvider = Initiator.load("com.tencent.mobileqq.setting.main.MainSettingConfigProvider");
            // 9.1.20+, NewSettingConfigProvider, A/B test on 9.1.20
            Class<?> kNewSettingConfigProvider = Initiator.load("com.tencent.mobileqq.setting.main.NewSettingConfigProvider");
            // 9.2.30, NewSettingConfigProvider was obfuscated to b
            Class<?> kNewSettingConfigProviderObf = Initiator.load("com.tencent.mobileqq.setting.main.b");
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] kMainSettingConfigProvider=" + kMainSettingConfigProvider
                    + " kNewSettingConfigProvider=" + kNewSettingConfigProvider
                    + " kNewSettingConfigProviderObf=" + kNewSettingConfigProviderObf);
            Method getItemProcessListOld = null;
            if (kMainSettingConfigProvider != null) {
                getItemProcessListOld = Reflex.findSingleMethod(kMainSettingConfigProvider, List.class, false, Context.class);
            }
            Method getItemProcessListNew = null;
            if (kNewSettingConfigProvider != null) {
                getItemProcessListNew = Reflex.findSingleMethod(kNewSettingConfigProvider, List.class, false, Context.class);
            }
            Method getItemProcessListNewObf = null;
            if (kNewSettingConfigProviderObf != null) {
                getItemProcessListNewObf = Reflex.findSingleMethod(kNewSettingConfigProviderObf, List.class, false, Context.class);
            }
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] getItemProcessList Old=" + getItemProcessListOld
                    + " New=" + getItemProcessListNew + " NewObf=" + getItemProcessListNewObf);
            if (getItemProcessListOld == null && getItemProcessListNew == null && getItemProcessListNewObf == null) {
                android.util.Log.e("FanqieDebug", "[SettingEntryHook] All getItemProcessList methods are null, throw!");
                throw new IllegalStateException("getItemProcessListOld == null && getItemProcessListNew == null && getItemProcessListNewObf == null");
            }
            Class<?> kAbstractItemProcessor = null;
            for (String possibleParent : new String[]{
                    "com.tencent.mobileqq.setting.main.processor.AccountSecurityItemProcessor",
                    "com.tencent.mobileqq.setting.main.processor.AboutItemProcessor"
            }) {
                Class<?> k = Initiator.load(possibleParent);
                if (k != null) {
                    kAbstractItemProcessor = k.getSuperclass();
                    break;
                }
            }
            if (kAbstractItemProcessor == null) {
                throw new IllegalStateException("kAbstractItemProcessor == null");
            }
            List<Class<?>> possibleSimpleItemProcessorCandidates = new ArrayList<>(6);
            // SimpleItemProcessor has too few xrefs. I have no idea how to find it without a list of candidates.
            final String[] possibleSimpleItemProcessorNames = new String[]{
                    // 8.9.70 ~ 9.0.0
                    "com.tencent.mobileqq.setting.processor.g",
                    // 9.0.8+
                    "com.tencent.mobileqq.setting.processor.h",
                    // 9.1.50 (9006)
                    "com.tencent.mobileqq.setting.processor.i",
                    // 9.1.70.25540 (9856) gray
                    "com.tencent.mobileqq.setting.processor.j",
                    // 9.1.28.21880 (8398) gray
                    "as3.i",
            };
            for (String name : possibleSimpleItemProcessorNames) {
                Class<?> klass = Initiator.load(name);
                if (klass != null && klass.getSuperclass() == kAbstractItemProcessor) {
                    possibleSimpleItemProcessorCandidates.add(klass);
                }
            }
            // use 'SimpleItemProcessor' keyword to search (9.2.10 ~ 9.3.10)
            if (requireMinQQVersion(QQVersion.QQ_9_2_10)) {
                Method m = DexKit.loadMethodFromCache(SimpleItemProcessor_Method.INSTANCE);
                if (m != null) {
                    Class<?> klass = m.getDeclaringClass();
                    if (klass.getSuperclass() == kAbstractItemProcessor && !possibleSimpleItemProcessorCandidates.contains(klass)) {
                        possibleSimpleItemProcessorCandidates.add(klass);
                    }
                }
            }
            // assert possibleSimpleItemProcessorCandidates.size() == 1;
            if (possibleSimpleItemProcessorCandidates.size() != 1) {
                throw new IllegalStateException("possibleSimpleItemProcessorCandidates.size() != 1, got " + possibleSimpleItemProcessorCandidates);
            }
            Class<?> kSimpleItemProcessor = possibleSimpleItemProcessorCandidates.get(0);
            Method setOnClickListener;
            {
                List<Method> candidates = ArraysKt.filter(kSimpleItemProcessor.getDeclaredMethods(), m -> {
                    Class<?>[] argt = m.getParameterTypes();
                    // NOSONAR java:S1872 not same class
                    return m.getReturnType() == void.class && argt.length == 1 && Function0.class.getName().equals(argt[0].getName());
                });
                candidates.sort(Comparator.comparing(Method::getName));
                // TIM 4.0.95.4001 only have one method, that is the one we need (onClick() lambda)
                if (candidates.size() != 2 && candidates.size() != 1) {
                    throw new IllegalStateException("com.tencent.mobileqq.setting.processor.g.?(Function0)V candidates.size() != 1|2");
                }
                // take the smaller one
                setOnClickListener = candidates.get(0);
            }
            Constructor<?> ctorSimpleItemProcessor;
            int ctorSimpleItemProcessorArgc;
            {
                Constructor<?> c = null;
                int i = 0;
                try {
                    // Since version QQ version X, where X <= 9.1.91.266545 (10298). I didn't attempt to find the exact value of X.
                    // tianshuPath : String? = null
                    c = kSimpleItemProcessor.getDeclaredConstructor(Context.class, int.class, CharSequence.class, int.class,
                            String.class);
                    i = 5;
                } catch (NoSuchMethodException ignored) {
                }
                if (c == null) {
                    c = kSimpleItemProcessor.getDeclaredConstructor(Context.class, int.class, CharSequence.class, int.class);
                    i = 4;
                }
                ctorSimpleItemProcessor = c;
                ctorSimpleItemProcessorArgc = i;
            }
            XC_MethodHook callback = HookUtils.afterAlways(this, 50, param -> {
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] getItemProcessList callback fired, providerClass=" + param.thisObject.getClass().getName());
                List<Object> result = (List<Object>) param.getResult();
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] result list size=" + (result != null ? result.size() : "null"));
                Context ctx = (Context) param.args[0];
                Class<?> kItemProcessorGroup = result.get(0).getClass();
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] ItemProcessorGroup class=" + kItemProcessorGroup.getName());
                Constructor<?> ctor;
                try {
                    ctor = kItemProcessorGroup.getDeclaredConstructor(List.class, CharSequence.class, CharSequence.class);
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] found 3-arg group ctor");
                } catch (NoSuchMethodException e) {
                    // 9.2.30
                    ctor = kItemProcessorGroup.getDeclaredConstructor(List.class, CharSequence.class, CharSequence.class,
                            int.class, load("kotlin.jvm.internal.DefaultConstructorMarker"));
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] found 5-arg group ctor (QQ 9.2.30+)");
                }
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] calling Parasitics.injectModuleResources...");
                Parasitics.injectModuleResources(ctx.getResources());
                @SuppressLint("DiscouragedApi")
                int resId = ctx.getResources().getIdentifier("qui_tuning", "drawable", ctx.getPackageName());
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] qui_tuning resId=0x" + Integer.toHexString(resId)
                        + " (0 means not found in host, may use fallback)");
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] R.id.setting2Activity_settingEntryItem=0x"
                        + Integer.toHexString(R.id.setting2Activity_settingEntryItem));
                Object entryItem;
                try {
                    if (ctorSimpleItemProcessorArgc == 5) {
                        entryItem = ctorSimpleItemProcessor.newInstance(ctx, R.id.setting2Activity_settingEntryItem, "小番茄解混淆", resId, null);
                    } else {
                        entryItem = ctorSimpleItemProcessor.newInstance(ctx, R.id.setting2Activity_settingEntryItem, "小番茄解混淆", resId);
                    }
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] entryItem created: " + entryItem);
                } catch (Throwable t) {
                    android.util.Log.e("FanqieDebug", "[SettingEntryHook] entryItem creation FAILED: " + t, t);
                    throw t;
                }
                Class<?> thatFunction0 = setOnClickListener.getParameterTypes()[0];
                Object theUnit = thatFunction0.getClassLoader().loadClass("kotlin.Unit").getField("INSTANCE").get(null);
                ClassLoader hostClassLoader = Initiator.getHostClassLoader();
                Object func0 = Proxy.newProxyInstance(hostClassLoader, new Class<?>[]{thatFunction0}, (proxy, method, args) -> {
                    if (method.getName().equals("invoke")) {
                        android.util.Log.i("FanqieDebug", "[SettingEntryHook] entry clicked! calling onSettingEntryClick");
                        onSettingEntryClick(ctx);
                        return theUnit;
                    }
                    // must be sth from Object
                    return method.invoke(this, args);
                });
                setOnClickListener.invoke(entryItem, func0);
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] onClickListener set");
                ArrayList<Object> list = new ArrayList<>(1);
                list.add(entryItem);
                Object group;
                if (ctor.getParameterTypes().length == 5) {
                    // 9.2.30
                    group = ctor.newInstance(list, "", "", 6, null);
                } else {
                    group = ctor.newInstance(list, "", "");
                }
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] group created: " + group);
                boolean isNew = param.thisObject.getClass().getName().contains("NewSettingConfigProvider");
                int indexToInsert = isNew ? 2 : 1;
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] isNew=" + isNew + ", inserting at index=" + indexToInsert
                        + " into result list size=" + result.size());
                result.add(indexToInsert, group);
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] ENTRY INJECTED SUCCESS! result new size=" + result.size());
            });
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] hooking getItemProcessList methods: Old=" + (getItemProcessListOld != null)
                    + " New=" + (getItemProcessListNew != null) + " NewObf=" + (getItemProcessListNewObf != null));
            if (getItemProcessListOld != null) {
                XposedBridge.hookMethod(getItemProcessListOld, callback);
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] hooked Old (MainSettingConfigProvider)");
            }
            if (getItemProcessListNew != null) {
                XposedBridge.hookMethod(getItemProcessListNew, callback);
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] hooked New (NewSettingConfigProvider)");
            }
            if (getItemProcessListNewObf != null) {
                XposedBridge.hookMethod(getItemProcessListNewObf, callback);
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] hooked NewObf (NewSettingConfigProvider obfuscated)");
            }
        }
    }

    private final XC_MethodHook mAddModuleEntry = new XC_MethodHook(51) {
        @Override
        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
            android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry callback fired (legacy QQ < 8.9.70 path), this=" + param.thisObject.getClass().getName());
            try {
                final Activity activity;
                var thisObject = param.thisObject;
                if (thisObject instanceof Activity) {
                    activity = (Activity) thisObject;
                } else {
                    activity = (Activity) Reflex.invokeVirtual(thisObject, "getActivity");
                }
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry activity=" + activity);
                Resources res = activity.getResources();
                Class<?> itemClass;
                View itemRef = null;
                {
                    Class<?> clz = load("com/tencent/mobileqq/widget/FormSimpleItem");
                    if (clz != null) {
                        // find a candidate view field
                        for (Field f : thisObject.getClass().getDeclaredFields()) {
                            if (f.getType() == clz && !Modifier.isStatic(f.getModifiers())) {
                                f.setAccessible(true);
                                View v = (View) f.get(thisObject);
                                if (v != null && v.getParent() != null) {
                                    itemRef = v;
                                    break;
                                }
                            }
                        }
                    }
                }
                android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry itemRef via FormSimpleItem field scan: " + itemRef);
                if (itemRef == null && (itemClass = load("com/tencent/mobileqq/widget/FormCommonSingleLineItem")) != null) {
                    itemRef = (View) Reflex.getInstanceObjectOrNull(activity, "a", itemClass);
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry itemRef via FormCommonSingleLineItem: " + itemRef);
                }
                if (itemRef == null) {
                    Class<?> clz = load("com/tencent/mobileqq/widget/FormCommonSingleLineItem");
                    if (clz == null) {
                        clz = load("com/tencent/mobileqq/widget/FormSimpleItem");
                    }
                    itemRef = (View) Reflex.getFirstNSFByType(activity, clz);
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry itemRef via getFirstNSFByType: " + itemRef);
                }
                View item;
                if (itemRef == null) {
                    // we are in triassic period?
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry itemRef==null, using FormSimpleItem fallback");
                    item = (View) Reflex.newInstance(load("com/tencent/mobileqq/widget/FormSimpleItem"), activity, Context.class);
                } else {
                    // modern age
                    item = (View) Reflex.newInstance(itemRef.getClass(), activity, Context.class);
                }
                item.setId(R.id.setting2Activity_settingEntryItem);
                Reflex.invokeVirtual(item, "setLeftText", "小番茄解混淆", CharSequence.class);
                Reflex.invokeVirtual(item, "setBgType", 2, int.class);
                if (HookInstaller.getFuncInitException() != null) {
                    Reflex.invokeVirtual(item, "setRightText", "[严重错误]", CharSequence.class);
                } else if (LicenseStatus.hasUserAcceptEula()) {
                    Reflex.invokeVirtual(item, "setRightText", BuildConfig.VERSION_NAME, CharSequence.class);
                } else {
                    Reflex.invokeVirtual(item, "setRightText", "[未激活]", CharSequence.class);
                }
                item.setOnClickListener(v -> {
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry item clicked!");
                    onSettingEntryClick(activity);
                });
                if (itemRef != null && !HostInfo.isQQHD()) {
                    //modern age
                    ViewGroup list = (ViewGroup) itemRef.getParent();
                    ViewGroup.LayoutParams reflp;
                    if (list.getChildCount() == 1) {
                        //junk!
                        list = (ViewGroup) list.getParent();
                        reflp = ((View) itemRef.getParent()).getLayoutParams();
                    } else {
                        reflp = itemRef.getLayoutParams();
                    }
                    ViewGroup.LayoutParams lp = null;
                    if (reflp != null) {
                        lp = new ViewGroup.LayoutParams(MATCH_PARENT, /*reflp.height*/WRAP_CONTENT);
                    }
                    int index = 0;
                    int account_switch = res.getIdentifier("account_switch", "id", list.getContext().getPackageName());
                    try {
                        if (account_switch > 0) {
                            View accountItem = (View) list.findViewById(account_switch).getParent();
                            if (accountItem != null && accountItem.getParent() != null) {
                                // fix up the parent for CHA
                                list = (ViewGroup) accountItem.getParent();
                            }
                            for (int i = 0; i < list.getChildCount(); i++) {
                                if (list.getChildAt(i) == accountItem) {
                                    index = i + 1;
                                    break;
                                }
                            }
                        }
                        if (index > list.getChildCount()) {
                            index = 0;
                        }
                    } catch (NullPointerException ignored) {
                    }
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry adding item at index=" + index
                            + " into ViewGroup childCount=" + list.getChildCount());
                    list.addView(item, index, lp);
                    fixBackgroundType(list, item, index);
                    android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry LEGACY ENTRY INJECTED SUCCESS!");
                } else {
                    // triassic period, we have to find the ViewGroup ourselves
                    int qqsetting2_msg_notify = res.getIdentifier("qqsetting2_msg_notify", "id", activity.getPackageName());
                    if (qqsetting2_msg_notify == 0) {
                        android.util.Log.e("FanqieDebug", "[SettingEntryHook] mAddModuleEntry triassic: qqsetting2_msg_notify id=0, cannot inject!");
                        throw new UnsupportedOperationException("R.id.qqsetting2_msg_notify not found in triassic period");
                    } else {
                        ViewGroup vg = (ViewGroup) activity.findViewById(qqsetting2_msg_notify).getParent().getParent();
                        android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry triassic: adding to ViewGroup " + vg);
                        vg.addView(item, 0, new ViewGroup.LayoutParams(MATCH_PARENT, /*reflp.height*/WRAP_CONTENT));
                        android.util.Log.i("FanqieDebug", "[SettingEntryHook] mAddModuleEntry TRIASSIC ENTRY INJECTED SUCCESS!");
                    }
                }
            } catch (Throwable e) {
                traceError(e);
                throw e;
            }
        }
    };

    private void onSettingEntryClick(@NonNull Context context) {
        if (HookInstaller.getFuncInitException() != null) {
            SettingsUiFragmentHostActivity.startActivityForFragment(context, FuncStatusDetailsFragment.class,
                    FuncStatusDetailsFragment.getBundleForLocation(FuncStatusDetailsFragment.TARGET_INIT_EXCEPTION));
        } else if (LicenseStatus.hasUserAcceptEula()) {
            context.startActivity(new Intent(context, SettingsUiFragmentHostActivity.class));
        } else {
            SettingsUiFragmentHostActivity.startActivityForFragment(context, EulaFragment.class, null);
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
        }
    }

    private void fixBackgroundType(@NonNull ViewGroup parent, @NonNull View itemView, int index) {
        int lastClusterId = index - 1;
        if (lastClusterId < 0) {
            // unexpected
            return;
        }
        // make QQ 8.8.80 happy
        try {
            Reflex.invokeVirtual(itemView, "setBgType", 0, int.class);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) itemView.getLayoutParams();
            lp.setMargins(0, LayoutHelper.dip2px(parent.getContext(), 15), 0, 0);
            parent.requestLayout();
        } catch (ReflectiveOperationException e) {
            Log.e(e);
        }
    }
}
