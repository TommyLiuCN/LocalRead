package com.example.localread

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localread.reader.ReaderActivity
import com.example.localread.ui.me.MeScreen
import com.example.localread.ui.me.MeViewModel
import com.example.localread.ui.shelf.ShelfScreen
import com.example.localread.ui.shelf.ShelfViewModel
import com.example.localread.ui.theme.LocalReadTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalReadTheme {
                MainScaffold(onOpenBook = { bookId ->
                    startActivity(ReaderActivity.intent(this, bookId))
                })
            }
        }
    }
}

@Composable
private fun MainScaffold(onOpenBook: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val shelfVm: ShelfViewModel = viewModel()
    val meVm: MeViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    val message by shelfVm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            shelfVm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                    label = { Text("书架") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Person, null) },
                    label = { Text("我") },
                )
            }
        },
    ) { padding ->
        if (tab == 0) {
            ShelfScreen(
                vm = shelfVm,
                contentPadding = padding,
                onOpenBook = onOpenBook,
            )
        } else {
            MeScreen(vm = meVm, contentPadding = padding)
        }
    }
}
