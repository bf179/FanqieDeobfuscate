/*
 * FanqieDeobfuscate - 小番茄图片批量解混淆模块
 * https://github.com/bf179/FanqieDeobfuscate
 *
 * This software is opensource software.
 */

package cc.ioctl.hook.msg

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import cc.hicore.QApp.QAppUtils
import cc.hicore.message.chat.SessionHooker
import cc.hicore.message.chat.SessionUtils
import cc.ioctl.util.Reflex
import com.github.kyuubiran.ezxhelper.utils.args
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.newInstance
import io.github.qauxv.R
import io.github.qauxv.base.RuntimeErrorTracer
import io.github.qauxv.base.annotation.FunctionHookEntry
import io.github.qauxv.base.annotation.UiItemAgentEntry
import io.github.qauxv.bridge.AppRuntimeHelper
import io.github.qauxv.bridge.ntapi.MsgServiceHelper
import io.github.qauxv.dsl.FunctionEntryRouter
import io.github.qauxv.hook.CommonSwitchFunctionHook
import io.github.qauxv.ui.CommonContextWrapper
import io.github.qauxv.ui.ResUtils
import io.github.qauxv.util.Initiator
import io.github.qauxv.util.Log
import io.github.qauxv.util.QQVersion
import io.github.qauxv.util.SyncUtils
import io.github.qauxv.util.Toasts
import io.github.qauxv.util.dexkit.MultiSelectBarVM
import io.github.qauxv.util.dexkit.MultiSelectToBottomIntent
import io.github.qauxv.util.dexkit.DexKit
import io.github.qauxv.util.requireMinQQVersion
import mqq.app.AppActivity
import xyz.nextalone.util.hookAfter
import xyz.nextalone.util.hookBefore
import xyz.nextalone.util.invoke
import xyz.nextalone.util.method
import xyz.nextalone.util.throwOrTrue
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * 批量小番茄解混淆 Hook。
 * 在 QQ NT 多选模式下的多选栏中添加"批量解混淆"按钮,
 * 支持多选图片消息后一键批量解混淆并保存到相册。
 *
 * 实现方式参考 [MultiActionHook]。
 */
@FunctionHookEntry
@UiItemAgentEntry
object FanqieBatchHook : CommonSwitchFunctionHook(
    targets = arrayOf(MultiSelectToBottomIntent, MultiSelectBarVM)
), SessionHooker.IAIOParamUpdate {

    override val name = "批量解混淆"
    override val description = "多选图片消息后批量解混淆。\n" +
        "在聊天中长按消息进入多选模式, 选择图片消息后点击多选栏中的批量解混淆按钮即可。"
    override val uiItemLocation = FunctionEntryRouter.Locations.Auxiliary.MESSAGE_CATEGORY
    override val isAvailable = QAppUtils.isQQnt()

    private var nt_aioParam: Any? = null

    public override fun initOnce() = throwOrTrue {
        if (!QAppUtils.isQQnt()) return@throwOrTrue

        // Hook 多选栏创建: MultiSelectBarVB.onCreateView
        Initiator.loadClass("com.tencent.mobileqq.aio.input.multiselect.MultiSelectBarVB")
            .method("onCreateView")!!
            .hookAfter(this) {
                val rootView = findViewNt(it.method.declaringClass, it.thisObject) ?: return@hookAfter
                val context = rootView.context as AppActivity
                val count = rootView.childCount
                if (rootView.findViewById<View?>(R.id.fanqieBatchDeobfView) != null) return@hookAfter

                val iconResId = R.drawable.ic_item_tool_72dp
                if (count >= 11) {
                    val enableTalkBack = rootView.getChildAt(1).contentDescription != null
                    val separator = View(context).apply {
                        layoutParams = rootView.getChildAt(0).layoutParams
                    }
                    rootView.addView(
                        createBatchButton(context, iconResId, enableTalkBack, it.thisObject).apply {
                            layoutParams = rootView.getChildAt(1).layoutParams
                        },
                        count - 2
                    )
                    rootView.addView(separator, count - 1)
                } else {
                    val enableTalkBack = rootView.getChildAt(0).contentDescription != null
                    rootView.addView(
                        createBatchButton(context, iconResId, enableTalkBack, it.thisObject),
                        count - 1
                    )
                }
            }

        // Hook 多选栏 VM: 拦截 intent 来获取选中消息列表并执行批量解混淆
        if (requireMinQQVersion(QQVersion.QQ_9_1_5_BETA_20015)) {
            DexKit.requireClassFromCache(MultiSelectBarVM).method("handleIntent")
        } else {
            Initiator.loadClass("com.tencent.mobileqq.aio.input.multiselect.MultiSelectBarVM").method("handleIntent")
        }!!.hookBefore(this) {
            val intent = it.args[0]
            val intentClass = DexKit.requireClassFromCache(MultiSelectToBottomIntent)
            if (intent.javaClass.isAssignableFrom(intentClass)) {
                val flags = Reflex.getFirstByType(intent, Int::class.java)
                if (flags != -114514) return@hookBefore
                // 批量解混淆触发
                val mContext = it.thisObject.invoke("getMContext")!!
                val multiSelectUtilClazz = Initiator.loadClass(
                    "com.tencent.mobileqq.aio.msglist.holder.component.multifoward.b"
                )
                val selectUtil = Reflex.getStaticObject(multiSelectUtilClazz, "a")
                val m = multiSelectUtilClazz.findMethod { returnType.isAssignableFrom(List::class.java) }
                val msgList = (m.invoke(selectUtil, mContext) as List<*>)
                    .mapNotNull { msg ->
                        try {
                            val msgId = msg!!.invoke("getMsgId") as Long
                            msgId to msg
                        } catch (e: Exception) {
                            Log.e("FanqieBatchHook: failed to get msgId", e)
                            null
                        }
                    }
                Log.d("FanqieBatchHook: selected ${msgList.size} messages")
                thread {
                    batchDeobfuscate(mContext as Context, msgList)
                }
                it.result = null
            }
        }
    }

    /**
     * 对选中的消息列表执行批量解混淆。
     * 仅处理已缓存的图片消息, 跳过未缓存和非图片消息。
     */
    private fun batchDeobfuscate(context: Context, msgList: List<Pair<Long, Any>>) {
        var successCount = 0
        var skipCount = 0
        var failCount = 0
        val totalCount = msgList.size

        SyncUtils.runOnUiThread {
            Toasts.info(context, "开始批量解混淆 (共 $totalCount 条消息)...")
        }

        // 获取解混淆 Key (复用 FanqieDeobfuscateHook 的配置)
        val key = try {
            FanqieDeobfuscateHook.currentKey
        } catch (e: Exception) {
            FanqieDeobfuscateUtils.DEFAULT_KEY
        }

        for ((index, pair) in msgList.withIndex()) {
            val (msgId, msg) = pair
            try {
                val filePath = getFilePathNt(msg)
                if (filePath == null) {
                    skipCount++
                    Log.d("FanqieBatchHook: msg $msgId - no local path, skip")
                    continue
                }
                val file = File(filePath)
                if (!file.exists()) {
                    skipCount++
                    Log.d("FanqieBatchHook: msg $msgId - file not cached, skip")
                    continue
                }
                // 检查是否为图片文件
                val fileName = file.name.lowercase()
                if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg") &&
                    !fileName.endsWith(".png") && !fileName.endsWith(".webp") &&
                    !fileName.endsWith(".bmp") && !fileName.endsWith(".gif")) {
                    // 尝试解码检查是否为有效图片
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(filePath, options)
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        skipCount++
                        Log.d("FanqieBatchHook: msg $msgId - not a valid image, skip")
                        continue
                    }
                }

                val progress = "${index + 1}/$totalCount"
                SyncUtils.runOnUiThread {
                    Toasts.info(context, "解混淆中... ($progress)")
                }

                val bitmap = FanqieDeobfuscateUtils.deobfuscate(file, key)
                saveBitmapToGallery(context, bitmap, msgId)
                bitmap.recycle()
                successCount++
                Log.d("FanqieBatchHook: msg $msgId deobfuscated successfully")
            } catch (oom: OutOfMemoryError) {
                failCount++
                Log.e("FanqieBatchHook: msg $msgId OOM, skip")
            } catch (e: Exception) {
                failCount++
                Log.e("FanqieBatchHook: msg $msgId failed", e)
            }
        }

        // 显示结果
        val resultMsg = buildString {
            append("批量解混淆完成!\n")
            append("成功: $successCount\n")
            if (skipCount > 0) append("跳过: $skipCount (未缓存或非图片)\n")
            if (failCount > 0) append("失败: $failCount")
        }
        SyncUtils.runOnUiThread {
            Toasts.success(context, resultMsg)
        }
    }

    private fun getFilePathNt(message: Any): String? {
        return try {
            val msgClass = Initiator.loadClass("com.tencent.mobileqq.aio.msg.AIOMsgItem")
            val clazz = Initiator.load("com.tencent.qqnt.aio.msg.api.impl.AIOMsgItemApiImpl")!!
            clazz.newInstance().invoke("getLocalPath", message, msgClass) as String?
        } catch (e: Exception) {
            Log.e("FanqieBatchHook: getFilePathNt failed", e)
            null
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, msgId: Long) {
        val name = "fanqie_batch_${msgId}_${System.currentTimeMillis()}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FanqieDeobf")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: throw IOException("无法创建媒体文件")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("写入失败")
                    }
                } ?: throw IOException("无法打开输出流")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "FanqieDeobf"
                )
                if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建目录")
                val file = File(dir, name)
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("写入失败")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FanqieBatchHook: save failed for msg $msgId", e)
            throw e
        }
    }

    private fun findViewNt(clazz: Class<*>, obj: Any): LinearLayout? {
        for (f in clazz.declaredFields) {
            if (f.type.name == "kotlin.Lazy") {
                f.isAccessible = true
                val lazyObj = f[obj]!!.invoke("getValue")
                if (lazyObj is LinearLayout && check(lazyObj))
                    return lazyObj
            }
        }
        return null
    }

    private fun check(rootView: LinearLayout): Boolean {
        val count = rootView.childCount
        if (count <= 2) return false
        for (i in 0 until count) {
            if (rootView.getChildAt(i) is android.widget.TextView) return false
        }
        return true
    }

    private fun createBatchButton(context: Context, resId: Int, enableTalkBack: Boolean, vb: Any): ImageView {
        val imageView = ImageView(context)
        if (enableTalkBack) {
            imageView.contentDescription = "批量解混淆"
        }
        imageView.setOnClickListener {
            triggerBatch(it.context, vb)
        }
        imageView.setImageResource(resId)
        imageView.id = R.id.fanqieBatchDeobfView
        return imageView
    }

    private fun triggerBatch(ctx: Context, vb: Any) {
        runCatching {
            val baseVB = Initiator.loadClass("com.tencent.mvi.mvvm.BaseVB")
            val intentClass = DexKit.requireClassFromCache(MultiSelectToBottomIntent)
            val flags: Int = -114514
            val intent = intentClass.newInstance(args(flags), argTypes(Int::class.java))!!
            baseVB.method("sendIntent")!!.invoke(vb, intent)
            (ctx as Activity).onBackPressed()
        }.onFailure {
            Log.e("FanqieBatchHook: trigger failed", it)
        }
    }

    override fun onAIOParamUpdate(AIOParam: Any?) {
        nt_aioParam = AIOParam
    }

    override val runtimeErrorDependentComponents: List<RuntimeErrorTracer> = listOf(SessionHooker.INSTANCE)
}
