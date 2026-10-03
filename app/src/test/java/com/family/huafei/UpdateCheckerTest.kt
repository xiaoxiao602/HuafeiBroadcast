package com.family.huafei

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `相同版本不算更新`() {
        assertFalse(UpdateChecker.isNewer("1.2", "1.2"))
        assertFalse(UpdateChecker.isNewer("v1.2.0", "1.2"))
        assertFalse(UpdateChecker.isNewer("1.3", "1.3.0"))
    }

    @Test
    fun `更高版本返回真`() {
        assertTrue(UpdateChecker.isNewer("v1.3.0", "1.2"))
        assertTrue(UpdateChecker.isNewer("1.2.1", "1.2"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.10", "1.9"))
    }

    @Test
    fun `更低版本返回假`() {
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.2"))
        assertFalse(UpdateChecker.isNewer("0.9", "1.0"))
    }

    @Test
    fun `异常输入按0处理不崩溃`() {
        assertFalse(UpdateChecker.isNewer("", "1.2"))
        assertFalse(UpdateChecker.isNewer("abc", "1.2"))
        assertTrue(UpdateChecker.isNewer("1.2.1beta", "1.2"))
    }
}
