package com.example.tpglstock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.tpglstock.ui.AppNavigation
import com.example.tpglstock.ui.theme.TPGLStockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TPGLStockTheme {
                AppNavigation()
            }
        }
    }
}
