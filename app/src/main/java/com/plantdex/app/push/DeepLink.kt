package com.plantdex.app.push

import android.content.Intent

/** 알림을 눌러 앱을 열었을 때 갈 곳 */
data class DeepLink(val entryId: String?, val actorUid: String?) {
    companion object {
        /** 푸시 알림의 data (type, entryId, actorUid) 가 담긴 Intent 에서 읽습니다. */
        fun from(intent: Intent?): DeepLink? {
            val extras = intent?.extras ?: return null
            val type = extras.getString("type") ?: return null
            val entryId = extras.getString("entryId")?.takeIf { it.isNotBlank() }
            val actorUid = extras.getString("actorUid")?.takeIf { it.isNotBlank() }
            return when {
                entryId != null -> DeepLink(entryId, actorUid)
                type == "follow" && actorUid != null -> DeepLink(null, actorUid)
                else -> null
            }
        }
    }
}
