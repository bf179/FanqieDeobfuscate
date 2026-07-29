package xyz.nextalone.util

import com.github.kyuubiran.ezxhelper.utils.findMethod
import io.github.qauxv.util.xpcompat.XC_MethodHook
import io.github.qauxv.util.xpcompat.XposedBridge
import io.github.qauxv.util.xpcompat.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

// ---- Class extensions ----

val Class<*>.clazz: Class<*>
    get() = this

fun Class<*>.method(name: String): Method? =
    declaredMethods.firstOrNull { it.name == name }

fun Class<*>.methodOrThrow(name: String): Method =
    method(name) ?: throw NoSuchMethodException("$name not found in $this")

// ---- Method hook extensions (owner for error tracing) ----

fun Method.hookAfter(owner: Any, callback: (XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook(50) {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                callback(param)
            } catch (t: Throwable) {
                io.github.qauxv.util.Log.e(t)
            }
        }
    })

fun Method.hookBefore(owner: Any, callback: (XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook(50) {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                callback(param)
            } catch (t: Throwable) {
                io.github.qauxv.util.Log.e(t)
            }
        }
    })

// ---- Constructor hooks ----

fun Class<*>.hookAfterAllConstructors(owner: Any, callback: (XC_MethodHook.MethodHookParam) -> Unit) {
    for (ctor in this.declaredConstructors) {
        XposedBridge.hookMethod(ctor, object : XC_MethodHook(50) {
            override fun afterHookedMethod(param: MethodHookParam) {
                try { callback(param) } catch (t: Throwable) { io.github.qauxv.util.Log.e(t) }
            }
        })
    }
}

inline fun <reified T> hookAfterAllConstructors(owner: Any, crossinline callback: (XC_MethodHook.MethodHookParam) -> Unit) {
    val clazz = T::class.java
    for (ctor in clazz.declaredConstructors) {
        XposedBridge.hookMethod(ctor, object : XC_MethodHook(50) {
            override fun afterHookedMethod(param: MethodHookParam) {
                try { callback(param) } catch (t: Throwable) { io.github.qauxv.util.Log.e(t) }
            }
        })
    }
}

inline fun <reified T> hookBeforeAllConstructors(owner: Any, crossinline callback: (XC_MethodHook.MethodHookParam) -> Unit) {
    val clazz = T::class.java
    for (ctor in clazz.declaredConstructors) {
        XposedBridge.hookMethod(ctor, object : XC_MethodHook(50) {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try { callback(param) } catch (t: Throwable) { io.github.qauxv.util.Log.e(t) }
            }
        })
    }
}

// ---- Reflection extensions ----

@Suppress("UNCHECKED_CAST")
fun <T> Any.get(name: String): T {
    var clazz: Class<*> = this.javaClass
    while (clazz != Any::class.java) {
        try {
            val f: Field = clazz.getDeclaredField(name)
            f.isAccessible = true
            return f.get(this) as T
        } catch (_: NoSuchFieldException) {
            clazz = clazz.superclass
        }
    }
    throw NoSuchFieldException("$name not found in ${this.javaClass}")
}

fun Any.set(name: String, value: Any?) {
    var clazz: Class<*> = this.javaClass
    while (clazz != Any::class.java) {
        try {
            val f: Field = clazz.getDeclaredField(name)
            f.isAccessible = true
            f.set(this, value)
            return
        } catch (_: NoSuchFieldException) {
            clazz = clazz.superclass
        }
    }
    throw NoSuchFieldException("$name not found in ${this.javaClass}")
}

// ---- Generic invoke ----

fun Any.invoke(methodName: String, vararg args: Any?): Any? {
    val clazz = this.javaClass
    for (m in clazz.methods) {
        if (m.name == methodName && m.parameterTypes.size == args.size) {
            return m.invoke(this, *args)
        }
    }
    for (m in clazz.declaredMethods) {
        if (m.name == methodName && m.parameterTypes.size == args.size) {
            m.isAccessible = true
            return m.invoke(this, *args)
        }
    }
    throw NoSuchMethodException("$methodName not found in $clazz")
}

// ---- throwOrTrue for initOnce ----

fun throwOrTrue(block: () -> Unit): Boolean {
    block()
    return true
}

// ---- Method modifiers ----

val Method.isPublic: Boolean
    get() = Modifier.isPublic(modifiers)

val Method.isStatic: Boolean
    get() = Modifier.isStatic(modifiers)

// ---- View utilities ----

fun findHostView(clazz: Class<*>, obj: Any): android.view.View? {
    for (f in clazz.declaredFields) {
        if (f.type.name == "kotlin.Lazy") {
            f.isAccessible = true
            val lazyObj = f.get(obj)
            val value = lazyObj?.invoke("getValue")
            if (value is android.view.View) return value
        }
    }
    return null
}

fun hostDrawable(name: String): Int {
    val ctx = io.github.qauxv.util.Initiator.getHostClassLoader()
        ?: return 0
    return try {
        val resClass = ctx.loadClass("com.tencent.mobileqq.R\$drawable")
        val f = resClass.getDeclaredField(name)
        f.isAccessible = true
        f.getInt(null)
    } catch (_: Exception) { 0 }
}

fun hostLayout(name: String): Int {
    val ctx = io.github.qauxv.util.Initiator.getHostClassLoader()
        ?: return 0
    return try {
        val resClass = ctx.loadClass("com.tencent.mobileqq.R\$layout")
        val f = resClass.getDeclaredField(name)
        f.isAccessible = true
        f.getInt(null)
    } catch (_: Exception) { 0 }
}

// ---- Replace method result ----

fun Method.replace(owner: Any, replacement: (XC_MethodHook.MethodHookParam) -> Any?): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook(50) {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                param.result = replacement(param)
            } catch (t: Throwable) {
                io.github.qauxv.util.Log.e(t)
            }
        }
    })

// ---- putExFriend helper (used by HandleSendChatCount) ----

fun putExFriend(list: java.util.ArrayList<Any>, value: Any) {
    list.add(value)
}

fun today(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date())
}
