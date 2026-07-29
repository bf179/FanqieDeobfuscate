package com.xiaoniu.dispatcher

import cc.hicore.QApp.QAppUtils
import cc.ioctl.hook.msg.FanqieDeobfuscateHook
import cc.ioctl.util.HookUtils
import com.github.kyuubiran.ezxhelper.utils.isAbstract
import io.github.qauxv.util.xpcompat.XC_MethodHook
import io.github.qauxv.base.annotation.FunctionHookEntry
import io.github.qauxv.hook.BasePersistBackgroundHook
import io.github.qauxv.util.Initiator
import xyz.nextalone.util.hookAfterAllConstructors
import java.lang.reflect.Method

@FunctionHookEntry
object MenuBuilderHook : BasePersistBackgroundHook() {
    // Only Fanqie deobfuscation hook (all other hooks removed)
    private val decorators: Array<OnMenuBuilder> = arrayOf(
        FanqieDeobfuscateHook,
    )

    override fun initOnce(): Boolean {
        if (QAppUtils.isQQnt()) { // NT only
            val msgClass = Initiator.loadClass("com.tencent.mobileqq.aio.msg.AIOMsgItem")
            val baseContentComponentClass = Initiator.loadClass("com.tencent.mobileqq.aio.msglist.holder.component.BaseContentComponent")
            val getMsgMethod: Method = baseContentComponentClass.declaredMethods.first {
                it.returnType == msgClass && it.parameterTypes.isEmpty()
            }.apply { isAccessible = true }
            val listMethodName: String = baseContentComponentClass.declaredMethods.first {
                it.isAbstract && it.returnType == MutableList::class.java && it.parameterTypes.isEmpty()
            }.name
            val hookedClasses = mutableSetOf<Class<*>>()
            baseContentComponentClass.hookAfterAllConstructors(this) {
                val class_ContentComponent = it.thisObject.javaClass
                if (class_ContentComponent in hookedClasses) return@hookAfterAllConstructors
                hookedClasses.add(class_ContentComponent)
                val target = class_ContentComponent.name
                HookUtils.hookAfterAlways(this, class_ContentComponent.getMethod(listMethodName), 48) {
                    val msg = getMsgMethod.invoke(it.thisObject)!!
                    for (decorator in decorators) {
                        if (decorator.targetComponentTypes == null || target in decorator.targetComponentTypes!!) {
                            try {
                                decorator.onGetMenuNt(msg, target, it)
                            } catch (e: Exception) {
                                traceError(e)
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}

interface OnMenuBuilder {
    /**
     * [com.tencent.mobileqq.aio.msglist.holder.component.BaseContentComponent] 的子类名
     * null 表示所有
     */
    val targetComponentTypes: Array<String>?

    /**
     * 同一消息可能按照父类多次回调
     */
    @Throws(Exception::class)
    fun onGetMenuNt(
        msg: Any,
        componentType: String,
        param: XC_MethodHook.MethodHookParam
    )
}
