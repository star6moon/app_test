package com.plantdex.app.data.achievements

import android.content.Context

/**
 * 어떤 배지를 이미 축하했는지 기기에 기억합니다 (계정별).
 * 처음 설치해 기록이 많은 계정은 기존 배지를 한꺼번에 축하하지 않고 조용히 적어 둡니다.
 */
class BadgeLedger(context: Context) {
    private val prefs = context.getSharedPreferences("badges_seen", Context.MODE_PRIVATE)

    /** 아직 축하하지 않은 새 배지. 첫 호출이면 지금 얻은 것을 모두 적어 두고 빈 목록을 돌려줍니다. */
    fun newlyEarned(uid: String, badges: List<Badge>): List<Badge> {
        val key = "uid_$uid"
        val earned = badges.filter { it.earned }
        val seen = prefs.getStringSet(key, null)
        if (seen == null) {
            prefs.edit().putStringSet(key, earned.map { it.id }.toSet()).apply()
            return emptyList()
        }
        val fresh = earned.filter { it.id !in seen }
        if (fresh.isNotEmpty()) prefs.edit().putStringSet(key, seen + fresh.map { it.id }).apply()
        return fresh
    }

    /** 레벨이 올랐으면 새 레벨, 아니면 null. 처음 보는 계정이면 지금 레벨을 적어 두고 null. */
    fun levelUp(uid: String, level: Level): Level? {
        val key = "level_$uid"
        val seen = prefs.getInt(key, -1)
        if (seen == -1 || level.number > seen) prefs.edit().putInt(key, level.number).apply()
        return if (seen != -1 && level.number > seen) level else null
    }
}
