package com.jjrapps.constanza

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's DI entry point (design.md D5). No feature bindings live here yet — this scaffolds the
 * Hilt component graph so :app modules registered in later work units attach to it.
 */
@HiltAndroidApp
class ConstanzaApplication : Application()
