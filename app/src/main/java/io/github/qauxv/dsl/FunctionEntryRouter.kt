package io.github.qauxv.dsl

/**
 * [FanqieDeobfuscate] Minimal stub for DSL settings location constants.
 */
object FunctionEntryRouter {
    @JvmField
    object Locations {
        @JvmField
        object Auxiliary {
            @JvmField val MESSAGE_CATEGORY = arrayOf("消息")
            @JvmField val CHAT_CATEGORY = arrayOf("聊天")
            @JvmField val GROUP_CATEGORY = arrayOf("群聊")
            @JvmField val NOTIFICATION_CATEGORY = arrayOf("通知")
            @JvmField val FILE_CATEGORY = arrayOf("文件")
            @JvmField val MISC_CATEGORY = arrayOf("其他")
        }
        @JvmField
        object DebugCategory {
            @JvmField val DEBUG_CATEGORY = arrayOf("调试")
        }
        @JvmField
        object Simplify {
            @JvmField val UI_PROFILE = arrayOf("界面-资料卡")
            @JvmField val UI_CHAT_MSG = arrayOf("界面-消息")
            @JvmField val UI_MISC = arrayOf("界面-其他")
            @JvmField val MAIN_UI_MSG_LIST = arrayOf("主界面-消息列表")
            @JvmField val MAIN_UI_CONTACT = arrayOf("主界面-联系人")
            @JvmField val CHAT_GROUP_TITLE = arrayOf("群聊-标题栏")
            @JvmField val CHAT_GROUP_OTHER = arrayOf("群聊-其他")
            @JvmField val CHAT_OTHER = arrayOf("聊天-其他")
            @JvmField val SLIDING_UI = arrayOf("侧滑界面")
        }
    }
}
