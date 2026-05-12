package ru.myit.vlevpn.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.myit.vlevpn.domain.model.InstalledApp
import ru.myit.vlevpn.domain.repository.InstalledAppsRepository

@Singleton
class AndroidInstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledAppsRepository {
    override suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, flags)
        }

        resolved
            .asSequence()
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName?.takeIf { it != context.packageName } ?: return@mapNotNull null
                val appInfo = runCatching { packageManager.getApplicationInfoCompat(packageName) }.getOrNull()
                InstalledApp(
                    packageName = packageName,
                    label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: packageName,
                    isSystemApp = appInfo?.isSystemApp() == true,
                )
            }
            .distinctBy { it.packageName }
            .toList()
            .let(AppRoutingPriority::sort)
    }

    private fun PackageManager.getApplicationInfoCompat(packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            getApplicationInfo(packageName, 0)
        }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
