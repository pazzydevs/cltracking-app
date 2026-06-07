package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.Alignment
import com.example.ui.AppTab
import com.example.ui.CallTrackViewModel
import com.example.ui.DashboardScreen
import com.example.ui.PermissionsScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup edge to edge viewing
        enableEdgeToEdge()

        // Extract application dependency handles
        val app = application as CallTrackApplication
        
        val viewModel: CallTrackViewModel by viewModels {
            CallTrackViewModel.Factory(
                repository = app.callEventRepository,
                settingsManager = app.settingsManager
            )
        }

        setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: CallTrackViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CallTrack",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "CRM CONNECTOR",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 9.sp
                                ),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.Dashboard,
                    onClick = { viewModel.setTab(AppTab.Dashboard) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Settings,
                    onClick = { viewModel.setTab(AppTab.Settings) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Permissions,
                    onClick = { viewModel.setTab(AppTab.Permissions) },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Permissions") },
                    label = { Text("Onboarding") }
                )
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        
        when (currentTab) {
            AppTab.Dashboard -> DashboardScreen(
                viewModel = viewModel,
                modifier = screenModifier
            )
            AppTab.Settings -> SettingsScreen(
                viewModel = viewModel,
                modifier = screenModifier
            )
            AppTab.Permissions -> PermissionsScreen(
                modifier = screenModifier
            )
        }
    }
}
