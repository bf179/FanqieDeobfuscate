package cc.ioctl.fanqie

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.qauxv.BuildConfig

/**
 * 小番茄图片解混淆 - 主设置界面。
 * 提供 Xposed/LSPosed 模块的设置入口。
 */
class FanqieMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = """
                小番茄图片解混淆
                
                版本: ${BuildConfig.VERSION_NAME}
                
                本模块为 QQ Xposed/LSPosed 模块,
                安装后请在 LSPosed 管理器中激活,
                作用域勾选 QQ。
                
                功能:
                - 长按图片消息 → 解混淆
                - 多选图片 → 批量解混淆
                
                解混淆 Key 可在 QQ 中长按图片后
                通过设置项进行配置。
            """.trimIndent()
            setPadding(48, 48, 48, 48)
            textSize = 16f
            setLineSpacing(4f, 1.2f)
        }

        setContentView(tv)
    }
}
