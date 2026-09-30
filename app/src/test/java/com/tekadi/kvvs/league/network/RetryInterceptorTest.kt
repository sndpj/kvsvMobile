package com.tekadi.kvvs.league.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature: Super Admin role assign/revoke — acceptance criterion #3 ("optimize network issue
 * handling"). Covers RetryInterceptor.isRetryable's rule, in particular the newly-added
 * PATCH .../admin/users/{id}/role case, without needing to mock an OkHttp Interceptor.Chain.
 */
class RetryInterceptorTest {

    // ---------------- pre-existing rules (regression coverage) ----------------

    @Test
    fun everyGetRequestIsRetryable() {
        assertTrue(RetryInterceptor.isRetryable("GET", "/api/matches"))
        assertTrue(RetryInterceptor.isRetryable("GET", "/api/admin/users"))
    }

    @Test
    fun postBallIsRetryableBecauseItsIdempotentByClientBallUuid() {
        assertTrue(RetryInterceptor.isRetryable("POST", "/api/matches/12/ball"))
    }

    @Test
    fun mostMutatingEndpointsAreNotRetryable() {
        assertFalse(RetryInterceptor.isRetryable("POST", "/api/matches/12/toss"))
        assertFalse(RetryInterceptor.isRetryable("POST", "/api/matches/12/innings/start"))
        assertFalse(RetryInterceptor.isRetryable("PATCH", "/api/matches/12/bowler"))
        assertFalse(RetryInterceptor.isRetryable("POST", "/api/matches/12/ball/undo"))
    }

    // ---------------- new: Super Admin role update ----------------

    @Test
    fun patchAdminUserRoleIsRetryable() {
        assertTrue(RetryInterceptor.isRetryable("PATCH", "/api/admin/users/101/role"))
    }

    @Test
    fun gapCheck_aPatchEndingInRoleButNotUnderAdminUsersIsNotRetried() {
        // Guards the path scoping specifically — "/role" alone isn't enough to opt in; it must
        // be this exact, genuinely-idempotent endpoint.
        assertFalse(RetryInterceptor.isRetryable("PATCH", "/api/teams/5/role"))
    }

    @Test
    fun gapCheck_aGetUnderAdminUsersRoleIsAlreadyCoveredByThePlainGetRuleNotTheAdminSpecialCase() {
        assertTrue(RetryInterceptor.isRetryable("GET", "/api/admin/users/101/role"))
    }

    @Test
    fun postToTheSameAdminUsersRolePathIsNotRetried_onlyPatchIs() {
        assertFalse(RetryInterceptor.isRetryable("POST", "/api/admin/users/101/role"))
    }
}
