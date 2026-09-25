package com.example.tpglstock.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tpglstock.AppContainer
import com.example.tpglstock.TPGLApp

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as TPGLApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } },
    )
}
