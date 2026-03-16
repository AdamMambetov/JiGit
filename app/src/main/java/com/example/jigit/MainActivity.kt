package com.example.jigit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.example.jigit.ui.theme.JiGitTheme
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import java.io.File

private const val EMPTY_PATH = "Empty Git"
private const val SP_NAME = "jigit_prefs"
private const val GIT_FOLDER_SP_KEY = "git_path"
private var git: Git? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запрос разрешения MANAGE_EXTERNAL_STORAGE для Android 11+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:$packageName".toUri()
                startActivity(intent)
            }
        }

        enableEdgeToEdge()
        setContent {
            JiGitTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var gitPath by remember { mutableStateOf(EMPTY_PATH) }

    val gitPathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val path = getPathFromUri(uri = uri)
        if (path.isNotEmpty()) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(
                    uri!!,
                    takeFlags,
                )
            } catch (e: SecurityException) {
                Log.e("SettingsScreen", "Failed to take URI permission: ${e.message}")
            }
            gitPath = path
        }
    }

    LaunchedEffect(key1 = Unit) {
        val storedNotePath = getGitFolderPath(context)
        if (storedNotePath.isEmpty()) {
            gitPath = EMPTY_PATH
            return@LaunchedEffect
        }
        gitPath = getPathFromUri(uri = storedNotePath.toUri())
        openGit(gitPath)
    }

    Column(
        modifier = modifier,
    ) {
        if (gitPath === EMPTY_PATH) {
            Button(
                onClick = { gitPathLauncher.launch(null) },
            ) {
                Text(text = "Open Git Repo")
            }
        } else {
            Text(
                text = gitPath,
            )

            Button(
                onClick = { callGitInit(gitPath) },
            ) {
                Text(text = "Git Init")
            }
            Button(
                onClick = { callGitStatus() },
            ) {
                Text(text = "Git Status")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    JiGitTheme {
        MainScreen()
    }
}

fun openGit(repoPath: String) {
    git =  try {
        Git.open(File(repoPath))
    } catch (_: RepositoryNotFoundException) {
        null
    }
}

fun callGitInit(repoPath: String) {
    val gitDir = File(repoPath)

    // Проверяем, существует ли уже .git папка
    if (File(gitDir, ".git").exists()) {
        Log.e("callGitInit", "Git repository already exists at $repoPath")
        git = Git.open(gitDir)
        return
    }

    // Проверяем права на запись
    if (!gitDir.canWrite()) {
        Log.e("callGitInit", "No write permission for $repoPath")
        throw SecurityException("No write permission for $repoPath")
    }

    git = Git.init()
        .setDirectory(gitDir)
        .call()
}

fun callGitStatus() {
    if (git == null)
        return
    val result = git!!.status().call()
    if (result.isClean)
        Log.d("TAG", "Repo is clean")
    else
        Log.d("TAG", "Added: ${result.added}\nChanged: ${result.changed}\nUncommited changes: ${result.uncommittedChanges}\nUntracked: ${result.untracked}")
//    Git.open(File(repoPath)).status().call()
}

fun getPathFromUri(uri: Uri?): String {
    // Environment.getStorageDirectory() is "/storage"
    // Environment.getExternalStorageDirectory() is "/storage/emulated/0"
    // it.pathSegments[0] is "tree", [1] is "primary:your/selected/path"

    val storageDirPath = Environment.getStorageDirectory().path
    val externalStorageDirPath = Environment.getExternalStorageDirectory().path
    uri?.let {
        return if (it.path!!.contains("primary"))
            it.pathSegments[1]
                .replaceFirst(oldValue = "primary", newValue = externalStorageDirPath)
                .replaceFirst(oldValue = ":", newValue = "/")
        else
            "$storageDirPath/${it.pathSegments[1].replaceFirst(oldValue = ":", newValue = "/")}"
    }
    return ""
}

fun getGitFolderPath(context: Context): String {
    return context
        .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        .getString(GIT_FOLDER_SP_KEY, "")
        ?: ""
}
