package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialThreadTest {
    @Test
    fun nestsRepliesWithoutDroppingOrphanedContent() {
        val root = SocialComment(1, 9, "Mira", "root", 1)
        val child = SocialComment(2, 9, "User", "child", 2, parentId = 1)
        val grandchild = SocialComment(3, 9, "Mira", "deep", 3, parentId = 2)
        val orphan = SocialComment(4, 9, "Noa", "orphan", 4, parentId = 99)

        assertEquals(
            listOf(1L to 0, 2L to 1, 3L to 2, 4L to 0),
            threadedComments(listOf(child, orphan, grandchild, root)).map { it.first.id to it.second },
        )
    }
}
