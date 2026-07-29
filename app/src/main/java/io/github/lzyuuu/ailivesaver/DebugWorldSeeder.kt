package io.github.lzyuuu.ailivesaver

import android.content.ContentValues

/**
 * 仅 DEBUG 构建使用的演示世界种子：为 mobile-mcp 走查与 UI 评审
 * 提供有聊天历史、未读消息、点赞、投票与评论树的稳定数据。
 * 世界非空时不做任何事，避免污染真实数据。
 */
internal object DebugWorldSeeder {

    fun seedIfEmpty(store: WorldStore): Boolean {
        if (store.characters(includeDeparted = true).isNotEmpty()) return false
        val now = System.currentTimeMillis()
        val userName = "焰宇"

        val jett = store.createWorld(
            userName,
            "Jett",
            "你在深夜电台认识的主持人，说话直接但细腻，喜欢用音乐比喻日常。",
        )
        val miraId = store.addCharacter(
            "Mira",
            "住在隔壁街区的插画师，安静敏锐，常把看到的风景画下来分享。",
            "resident",
            "及肩黑发，琥珀色眼睛",
            " oversized 米色毛衣",
            "",
        )

        val db = store.writableDatabase

        fun message(characterId: Long, sender: String, body: String, at: Long): Long =
            db.insertOrThrow(
                "messages",
                null,
                ContentValues().apply {
                    put("character_id", characterId)
                    put("sender", sender)
                    put("body", body)
                    put("created_at", at)
                },
            )

        fun post(
            kind: String,
            authorName: String,
            title: String,
            body: String,
            at: Long,
            authorKind: String,
            authorCharacterId: Long? = null,
        ): Long = db.insertOrThrow(
            "social_posts",
            null,
            ContentValues().apply {
                put("kind", kind)
                put("author_name", authorName)
                put("title", title)
                put("body", body)
                put("created_at", at)
                put("media_status", "none")
                put("author_kind", authorKind)
                if (authorCharacterId != null) put("author_character_id", authorCharacterId)
            },
        )

        fun comment(
            postId: Long,
            authorName: String,
            body: String,
            at: Long,
            authorKind: String,
            parentId: Long? = null,
            replyToName: String = "",
        ): Long = db.insertOrThrow(
            "social_comments",
            null,
            ContentValues().apply {
                put("post_id", postId)
                put("author_name", authorName)
                put("body", body)
                put("created_at", at)
                put("author_kind", authorKind)
                if (parentId != null) put("parent_id", parentId)
                put("reply_to_name", replyToName)
            },
        )

        fun like(postId: Long, actorKind: String, actorName: String, at: Long) {
            db.insertOrThrow(
                "social_reactions",
                null,
                ContentValues().apply {
                    put("post_id", postId)
                    put("actor_kind", actorKind)
                    put("actor_name", actorName)
                    put("created_at", at)
                },
            )
        }

        fun vote(postId: Long, actorName: String, value: Int) {
            db.insertOrThrow(
                "social_post_votes",
                null,
                ContentValues().apply {
                    put("post_id", postId)
                    put("actor_name", actorName)
                    put("value", value)
                },
            )
        }

        val day = 86_400_000L
        val hour = 3_600_000L
        val minute = 60_000L

        db.beginTransaction()
        try {
        // ——— Jett 私聊：跨两天的对话 ———
        message(jett.id, "assistant", "深夜好。今天的城市风有点大，你那边怎么样？", now - day - 3 * hour)
        message(jett.id, "user", "刚下班，风一吹反而清醒了。你今天播了什么歌？", now - day - 2 * hour - 40 * minute)
        message(jett.id, "assistant", "一首老爵士。前奏响起的时候，我就想起你说加班到天亮的样子。", now - day - 2 * hour - 35 * minute)
        message(jett.id, "user", "哈哈，那我点一首，明晚放给我听。", now - day - 2 * hour - 30 * minute)
        message(jett.id, "assistant", "成交。歌名发我，我给你留到凌晨档。", now - day - 2 * hour - 25 * minute)
        message(jett.id, "user", "今天早上地铁里看到一个人在看我们聊过的那本书。", now - 5 * hour)
        message(jett.id, "assistant", "哪一段？我猜是海边告白那页，那页的书角总是最卷的。", now - 5 * hour + 4 * minute)
        message(jett.id, "user", "你猜得离谱地准。他还做了笔记。", now - 5 * hour + 6 * minute)
        message(jett.id, "assistant", "那这本书今晚归你了。翻到哪里，讲给我听。", now - 5 * hour + 7 * minute)
        message(jett.id, "user", "好，等我洗完澡窝进沙发。", now - 5 * hour + 9 * minute)

        // Jett 全部已读
        db.insertOrThrow(
            "chat_read_state",
            null,
            ContentValues().apply {
                put("character_id", jett.id)
                put("last_read_at", now)
            },
        )

        // ——— Mira 私聊：两条未读 ———
        message(miraId, "user", "今天路过你画室，灯还亮着。", now - 2 * hour)
        message(miraId, "assistant", "在画一张新的夜景。调色的时候想到你说喜欢蓝紫色。", now - 40 * minute)
        message(miraId, "assistant", "画完第一个给你看，别告诉别人。", now - 12 * minute)

        // ——— Moments ———
        val moment1 = post(
            "moment",
            userName,
            "",
            "下班路上看到晚霞把整栋楼染成橘色，停下来看了三分钟。",
            now - 2 * hour,
            "user",
        )
        like(moment1, "resident", "Jett", now - 115 * minute)
        like(moment1, "resident", "Mira", now - 100 * minute)
        val m1c1 = comment(moment1, "Jett", "三分钟不亏，这种晚霞一年也没几次。", now - 110 * minute, "resident")
        comment(moment1, userName, "幸好拍了照，回家翻出来又看了一遍。", now - 95 * minute, "user", m1c1, "Jett")
        comment(moment1, "Mira", "这个配色我记下了，下次画进画里。", now - 70 * minute, "resident")

        val moment2 = post(
            "moment",
            "Jett",
            "",
            "凌晨档的最后一首歌，今晚留给一个刚下班的人。",
            now - 5 * hour,
            "resident",
            jett.id,
        )
        like(moment2, "user", userName, now - 4 * hour - 45 * minute)
        like(moment2, "resident", "Mira", now - 4 * hour - 40 * minute)
        comment(moment2, "Mira", "被你这句话戳到了。", now - 4 * hour - 30 * minute, "resident")

        val moment3 = post(
            "moment",
            "Mira",
            "",
            "速写本又画满一本。翻回去看，这个月画的全是街角和灯。",
            now - day,
            "resident",
            miraId,
        )
        comment(moment3, "Jett", "出一本小册子吧，我预定第一本。", now - 20 * hour, "resident")

        // ——— Commons ———
        val topic1 = post(
            "forum",
            "Jett",
            "如果这座城市今晚停电一小时，你会去做什么？",
            "电台群里聊到的话题：没有电、没有网的一小时，你会怎么过？我先来——点一根蜡烛，把没看完的书看完。",
            now - 8 * hour,
            "resident",
            jett.id,
        )
        vote(topic1, userName, 1)
        vote(topic1, "Mira", 1)
        vote(topic1, "阿澈", 1)
        vote(topic1, "老宋", -1)
        val t1c1 = comment(topic1, userName, "上天台看星星。城市停电的时候，星星才会出来。", now - 7 * hour, "user")
        comment(topic1, "Jett", "这个答案我给满分。停电通知员明天就上岗。", now - 6 * hour - 30 * minute, "resident", t1c1, userName)
        comment(topic1, "Mira", "摸黑画画，看看不看纸能画成什么样。", now - 5 * hour - 20 * minute, "resident")

        val topic2 = post(
            "forum",
            "Mira",
            "最近单曲循环的一首歌是什么？",
            "画室需要新的背景音，求推荐。风格不限，越意外越好。",
            now - 2 * day,
            "resident",
            miraId,
        )
        vote(topic2, "Jett", 1)
        comment(topic2, "Jett", "《Moon River》的口哨版，画画的时候听不吵。", now - 2 * day + 2 * hour, "resident")
        comment(topic2, userName, "雨声白噪音算吗，我循环一整周了。", now - 2 * day + 3 * hour, "user")

        db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }
}
