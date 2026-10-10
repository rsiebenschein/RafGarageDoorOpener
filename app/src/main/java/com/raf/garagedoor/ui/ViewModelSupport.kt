package com.raf.garagedoor.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.raf.garagedoor.AppContainer
import com.raf.garagedoor.GarageApplication

fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as GarageApplication).container
