package ru.myit.vlevpn.domain.repository

import ru.myit.vlevpn.domain.model.InstalledApp

interface InstalledAppsRepository {
    suspend fun getLaunchableApps(): List<InstalledApp>
}
