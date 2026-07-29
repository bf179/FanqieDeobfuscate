package xyz.nextalone.util

import io.github.qauxv.util.xpcompat.XC_MethodHook
import io.github.qauxv.util.xpcompat.XposedBridge
import io.github.qauxv.util.Log
import java.lang.reflect.Method

object HookUtilsKt {
    fun hookMethod(method: Method, callback: (XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook.Unhook {
        return XposedBridge.hookMethod(method, object : XC_MethodHook(50) {
            override fun afterHookedMethod(param: MethodHookParam) {
                try { callback(param) } catch (t: Throwable) { Log.e(t) }
            }
        })
    }
}
