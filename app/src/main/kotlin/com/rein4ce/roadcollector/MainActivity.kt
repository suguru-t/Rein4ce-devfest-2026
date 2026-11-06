package com.rein4ce.roadcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rein4ce.roadcollector.ui.AppNav
import com.rein4ce.roadcollector.ui.theme.RoadCollectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoadCollectorTheme {
                AppNav()
            }
        }
    }
}
