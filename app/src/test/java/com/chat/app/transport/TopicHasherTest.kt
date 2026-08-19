package com.chat.app.transport

import com.chat.app.transport.relay.TopicHasher
import org.junit.Assert.*
import org.junit.Test

class TopicHasherTest {

    @Test
    fun testDeterministicTopicHashing() {
        val userId = "alice-550e8400-e29b-41d4-a716-446655440000"
        val hash1 = TopicHasher.hashTopic(userId)
        val hash2 = TopicHasher.hashTopic(userId)

        assertEquals("Same user ID must produce identical topic hash", hash1, hash2)
        assertTrue("Topic hash must start with p2p_chat_ prefix", hash1.startsWith("p2p_chat_"))
        assertFalse("Topic hash must NOT contain plaintext user ID", hash1.contains("alice"))
    }

    @Test
    fun testDistinctUsersProduceDistinctHashes() {
        val userAlice = "alice-uuid-111"
        val userBob = "bob-uuid-222"

        val hashAlice = TopicHasher.hashTopic(userAlice)
        val hashBob = TopicHasher.hashTopic(userBob)

        assertNotEquals("Different users must produce distinct topic hashes", hashAlice, hashBob)
    }

    @Test
    fun testBlankUserIdHandling() {
        val hash = TopicHasher.hashTopic("   ")
        assertEquals("p2p_chat_global", hash)
    }
}
