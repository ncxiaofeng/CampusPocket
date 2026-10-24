package com.ecut.campuspocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ecut.campuspocket.ui.CampusPocketRoot
import com.ecut.campuspocket.ui.theme.CampusPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusPocketTheme {
                CampusPocketRoot()
            }
        }
    }
}
