package com.xtreamtv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xtreamtv.design.AppTheme
import com.xtreamtv.ui.AppRoot
import com.xtreamtv.ui.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = (application as XtreamApp).container
        setContent {
            val vm: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } })
            AppTheme { AppRoot(vm) }
        }
    }
}
