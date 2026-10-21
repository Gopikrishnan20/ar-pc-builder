package com.arpcbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arpcbuilder.ui.ar.ARBuilderScreen
import com.arpcbuilder.ui.theme.ARPCBuilderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Camera permission and ARCore install/availability are handled by
 * `ARSceneView` itself (its permission handler and availability overlay), so no extra gate is needed here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ARPCBuilderTheme {
                ARBuilderScreen()
            }
        }
    }
}
