package com.tekadi.kvvs.league

import android.app.Application
import android.content.Context

/**
 * KvsvRequest1.2 #9: the token-refresh Authenticator (network/TokenAuthenticator.kt) needs a
 * Context to persist the refreshed session via TokenStore (DataStore-backed), but it runs
 * inside OkHttp — outside any Composable or ViewModel that would normally supply one. This is
 * the standard, safe way to get one there: an Application Context (not an Activity Context, so
 * no leak risk) captured once at process start.
 */
class KvsvApplication : Application() {
    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
