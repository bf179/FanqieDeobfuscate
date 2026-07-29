/*
 * QAuxiliary - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2026 QAuxiliary developers
 * https://github.com/cinit/QAuxiliary
 *
 * This software is an opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version as published
 * by QAuxiliary contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this software.  If not, see
 * <https://github.com/cinit/QAuxiliary/blob/master/LICENSE.md>.
 */

package cc.ioctl.hook.msg

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import cc.hicore.QApp.QAppUtils
import com.xiaoniu.dispatcher.OnMenuBuilder
import com.xiaoniu.util.ContextUtils
import io.github.qauxv.R
import io.github.qauxv.base.IEntityAgent
import io.github.qauxv.base.annotation.FunctionHookEntry
import io.github.qauxv.base.annotation.UiItemAgentEntry
import io.github.qauxv.dsl.FunctionEntryRouter
import io.github.qauxv.hook.CommonSwitchFunctionHook
import io.github.qauxv.ui.CommonContextWrapper
import io.github.qauxv.util.CustomMenu
import io.github.qauxv.util.Initiator
import io.github.qauxv.util.SyncUtils
import io.github.qauxv.util.Toasts
import io.github.qauxv.util.dexkit.AbstractQQCustomMenuItem
import io.github.qauxv.util.xpcompat.XC_MethodHook
import me.ketal.data.ConfigData
import xyz.nextalone.util.invoke
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * 长按图片消息对图片进行"小番茄"解混淆。
 *
 * 小番茄图片混淆基于 Gilbert(广义 Hilbert)空间填充曲线对像素位置进行置换,
 * 并使用黄金比例偏移 offset = round(key * W * H) 做循环移位。key 默认为黄金比例
 * 共轭 (sqrt(5)-1)/2 ≈ 0.618, 也可在设置页自定义 (范围 (0, 1.618])。
 *
 * 混淆: dst[curve[(i+offset)%n]] = src[curve[i]]; 解混淆为其逆运算: dst[curve[i]] = src[curve[(i+offset)%n]]。
 *
 * 功能入口与配置位置参考 [PicMd5Hook]。
 */
@FunctionHookEntry
@UiItemAgentEntry
object FanqieDeobfuscateHook : CommonSwitchFunctionHook(
    targets = arrayOf(AbstractQQCustomMenuItem)
), OnMenuBuilder {

    override val name = "小番茄解混淆"
    override val description = "长按图片消息进行小番茄(Gilbert 曲线)解混淆并保存到相册。多选图片后可在多选栏点击批量解混淆。"
    override val uiItemLocation = FunctionEntryRouter.Locations.Auxiliary.MESSAGE_CATEGORY
    override val isAvailable = QAppUtils.isQQnt()

    private val keyConfig = ConfigData<Double>("fanqie_deobf_key")

    val currentKey: Double
        get() {
            val v: Double? = keyConfig.getValue()
            return if (v == null || v <= 0.0 || v > 1.618) FanqieDeobfuscateUtils.DEFAULT_KEY else v
        }

    override fun initOnce(): Boolean = true

    override val targetComponentTypes = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent"
    )

    override val uiItemAgent: io.github.qauxv.base.IUiItemAgent
        get() = keyConfigItemAgent

    private val keyConfigItemAgent: io.github.qauxv.base.IUiItemAgent = object : io.github.qauxv.base.IUiItemAgent {
        override val titleProvider: (IEntityAgent) -> String = { "小番茄解混淆" }
        override val summaryProvider: ((IEntityAgent, Context) -> CharSequence?) = { _, _ ->
            "长按图片消息进行小番茄(Gilbert 曲线)解混淆, 当前 Key: ${"%.4f".format(currentKey)}"
        }
        override val valueState: kotlinx.coroutines.flow.StateFlow<String?>? = null
        override val validator: ((io.github.qauxv.base.IUiItemAgent) -> Boolean) = { _ -> true }
        override val switchProvider: io.github.qauxv.base.ISwitchCellAgent? by lazy {
            object : io.github.qauxv.base.ISwitchCellAgent {
                override val isCheckable = true
                override var isChecked: Boolean
                    get() = isEnabled
                    set(value) {
                        if (value != isEnabled) {
                            isEnabled = value
                        }
                    }
            }
        }
        override val onClickListener: ((IEntityAgent, Activity, View) -> Unit) = { _, activity, _ ->
            showKeyConfigDialog(activity)
        }
        override val extraSearchKeywordProvider: ((IEntityAgent, Context) -> Array<String>?)? = null
    }

    override fun onGetMenuNt(msg: Any, componentType: String, param: XC_MethodHook.MethodHookParam) {
        if (!isEnabled) return
        val item = CustomMenu.createItemIconNt(msg, "解混淆", R.drawable.ic_item_tool_72dp, R.id.item_fanqie_deobfuscate) {
            val activity = ContextUtils.getCurrentActivity()
            val context = CommonContextWrapper.createAppCompatContext(activity)
            val file = File(getFilePathNt(msg))
            if (!file.exists()) {
                Toasts.info(context, "请先查看原图后重试")
                return@createItemIconNt
            }
            Toasts.info(context, "正在解混淆...")
            thread {
                try {
                    val result = deobfuscate(file)
                    SyncUtils.runOnUiThread { showResultDialog(context, result) }
                } catch (oom: OutOfMemoryError) {
                    SyncUtils.runOnUiThread { Toasts.error(context, "图片过大, 内存不足") }
                } catch (e: Exception) {
                    traceError(e)
                    SyncUtils.runOnUiThread {
                        Toasts.error(context, "解混淆失败: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        (param.result as MutableList<Any>).add(item)
    }

    private fun getFilePathNt(message: Any): String {
        val msgClass = Initiator.loadClass("com.tencent.mobileqq.aio.msg.AIOMsgItem")
        val clazz = Initiator.load("com.tencent.qqnt.aio.msg.api.impl.AIOMsgItemApiImpl")!!
        return clazz.newInstance().invoke("getLocalPath", message, msgClass) as String
    }

    // ---------------- 设置页 Key 配置弹窗 ----------------

    private fun showKeyConfigDialog(activity: Activity) {
        val ctx = CommonContextWrapper.createAppCompatContext(activity)
        val edit = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("%.6f".format(currentKey))
            setSelection(text.length)
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(edit)
        }
        AlertDialog.Builder(ctx)
            .setTitle("设置解混淆 Key")
            .setMessage("范围 (0, 1.618], 默认 ${"%.4f".format(FanqieDeobfuscateUtils.DEFAULT_KEY)} (黄金比例共轭)")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val raw = edit.text.toString().trim()
                val v = raw.toDoubleOrNull()
                if (v == null || v <= 0.0 || v > 1.618) {
                    Toasts.error(ctx, "请输入 (0, 1.618] 范围内的数字")
                    return@setPositiveButton
                }
                keyConfig.setValue(v)
                Toasts.success(ctx, "已保存: ${"%.4f".format(v)}")
            }
            .setNeutralButton("恢复默认") { _, _ ->
                keyConfig.remove()
                Toasts.success(ctx, "已恢复默认 ${"%.4f".format(FanqieDeobfuscateUtils.DEFAULT_KEY)}")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------------- 小番茄 (Gilbert 曲线 + 自定义 key 偏移) 解混淆 ----------------

    private fun deobfuscate(file: File): Bitmap {
        return FanqieDeobfuscateUtils.deobfuscate(file, currentKey)
    }

    // ---------------- 结果展示与保存 ----------------

    private fun showResultDialog(context: Context, bitmap: Bitmap) {
        val dm = context.resources.displayMetrics
        val maxW = (dm.widthPixels * 0.85).toInt()
        val maxH = (dm.heightPixels * 0.70).toInt()
        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = maxW
            maxHeight = maxH
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setImageBitmap(bitmap)
        }
        AlertDialog.Builder(context)
            .setTitle("解混淆结果")
            .setView(imageView)
            .setPositiveButton("保存到相册") { _, _ -> saveBitmapToGallery(context, bitmap) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        val name = "fanqie_deobf_${System.currentTimeMillis()}.png"
        thread {
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
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(file.absolutePath), arrayOf("image/png"), null
                    )
                }
                SyncUtils.runOnUiThread {
                    Toasts.success(context, "已保存到相册 Pictures/FanqieDeobf")
                }
            } catch (e: Exception) {
                traceError(e)
                SyncUtils.runOnUiThread {
                    Toasts.error(context, "保存失败: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }
}
