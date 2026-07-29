package io.github.qauxv.dsl

/**
 * Minimal stub for DSL settings location constants.
 */
object FunctionEntryRouter {
    object Locations {
        object Auxiliary {
            val MESSAGE_CATEGORY = arrayOf("消息")
            val CHAT_CATEGORY = arrayOf("聊天")
            val GROUP_CATEGORY = arrayOf("群聊")
            val NOTIFICATION_CATEGORY = arrayOf("通知")
            val FILE_CATEGORY = arrayOf("文件")
            val MISC_CATEGORY = arrayOf("其他")
        }
        object DebugCategory {
            val DEBUG_CATEGORY = arrayOf("调试")
        }
        object Simplify {
            val UI_PROFILE = arrayOf("界面-资料卡")
            val UI_CHAT_MSG = arrayOf("界面-消息")
            val UI_MISC = arrayOf("界面-其他")
            val MAIN_UI_MSG_LIST = arrayOf("主界面-消息列表")
            val MAIN_UI_CONTACT = arrayOf("主界面-联系人")
            val CHAT_GROUP_TITLE = arrayOf("群聊-标题栏")
            val CHAT_GROUP_OTHER = arrayOf("群聊-其他")
            val CHAT_OTHER = arrayOf("聊天-其他")
            val SLIDING_UI = arrayOf("侧滑界面")
        }
    }
}
