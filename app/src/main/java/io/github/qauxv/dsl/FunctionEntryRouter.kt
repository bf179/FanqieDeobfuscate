package io.github.qauxv.dsl

/**
 * [FanqieDeobfuscate] Minimal stub for DSL settings location constants.
 * The full DSL UI system has been removed; this provides only the
 * location strings needed by CommonSwitchFunctionHook base class.
 */
object FunctionEntryRouter {
    object Locations {
        object Auxiliary {
            const val MESSAGE_CATEGORY: Array<String> = arrayOf("消息")
            const val CHAT_CATEGORY: Array<String> = arrayOf("聊天")
            const val GROUP_CATEGORY: Array<String> = arrayOf("群聊")
            const val NOTIFICATION_CATEGORY: Array<String> = arrayOf("通知")
            const val FILE_CATEGORY: Array<String> = arrayOf("文件")
        }
        object Simplify {
            const val UI_PROFILE: Array<String> = arrayOf("界面-资料卡")
            const val UI_CHAT_MSG: Array<String> = arrayOf("界面-消息")
            const val UI_MISC: Array<String> = arrayOf("界面-其他")
            const val MAIN_UI_MSG_LIST: Array<String> = arrayOf("主界面-消息列表")
            const val MAIN_UI_CONTACT: Array<String> = arrayOf("主界面-联系人")
            const val CHAT_GROUP_TITLE: Array<String> = arrayOf("群聊-标题栏")
            const val CHAT_GROUP_OTHER: Array<String> = arrayOf("群聊-其他")
            const val CHAT_OTHER: Array<String> = arrayOf("聊天-其他")
            const val SLIDING_UI: Array<String> = arrayOf("侧滑界面")
        }
    }
}
