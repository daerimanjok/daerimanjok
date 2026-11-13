package app.daerimanjok

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.daerimanjok.core.car.CarSource
import app.daerimanjok.core.restrictions.DriveMode
import app.daerimanjok.feature.daeri.RecordingService
import app.daerimanjok.ui.AppRoot
import app.daerimanjok.ui.MainViewModel
import app.daerimanjok.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** True while the app is on screen; arrival alerts are only needed when it isn't. */
        @Volatile var visible = false
            private set
    }

    private val vm: MainViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onSpeedPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            AppTheme(themeMode) { AppRoot(vm) }
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        RecordingService.cancelArrival(this)
        vm.refreshNavi()
        // Only show the system permission dialog while parked (no dialogs while driving).
        if (vm.driveMode.value == DriveMode.DRIVING) return
        val needed = buildList {
            if (vm.needsSpeedPermission()) add(CarSource.SPEED_PERMISSION)
            // Arrival notification with the parking spot when the app is behind navigation.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }
}
