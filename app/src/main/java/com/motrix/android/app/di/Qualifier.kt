package com.motrix.android.app.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RpcUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WsUrl
