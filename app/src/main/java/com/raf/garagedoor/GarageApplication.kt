package com.raf.garagedoor

import android.app.Application

class GarageApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
