package com.embabel.shepherd.community.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RepoIdTest {

    @Test
    fun `should parse standard GitHub URL`() {
        val result = RepoId.fromUrl("https://github.com/owner/repo")

        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun `should parse GitHub URL with git suffix`() {
        val result = RepoId.fromUrl("https://github.com/myorg/myrepo.git")

        assertNotNull(result)
        assertEquals("myorg", result!!.owner)
        assertEquals("myrepo", result.repo)
    }

    @Test
    fun `should return null for non-GitHub URL`() {
        val result = RepoId.fromUrl("https://gitlab.com/owner/repo")

        assertNull(result)
    }

    @Test
    fun `should return null for invalid URL`() {
        val result = RepoId.fromUrl("not-a-url")

        assertNull(result)
    }

    @Test
    fun `should return null for URL without owner and repo`() {
        val result = RepoId.fromUrl("https://github.com/")

        assertNull(result)
    }

    @Test
    fun `should handle http protocol`() {
        val result = RepoId.fromUrl("http://github.com/owner/repo")

        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun `should handle URL with trailing slash`() {
        val result = RepoId.fromUrl("https://github.com/owner/repo/")

        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun `should handle URL with additional path segments`() {
        val result = RepoId.fromUrl("https://github.com/owner/repo/issues/123")

        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }
}
