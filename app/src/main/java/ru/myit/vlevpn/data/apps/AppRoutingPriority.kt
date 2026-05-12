package ru.myit.vlevpn.data.apps

import ru.myit.vlevpn.domain.model.InstalledApp

internal object AppRoutingPriority {
    fun sort(apps: List<InstalledApp>): List<InstalledApp> =
        apps.sortedWith(
            compareBy<InstalledApp> { priorityRank(it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.packageName },
        )

    private fun priorityRank(app: InstalledApp): Int {
        val index = priorityRules.indexOfFirst { it.matches(app) }
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private val priorityRules = listOf(
        PriorityRule(
            exactPackages = setOf("ru.oneme.app", "ru.max.app"),
            exactLabels = setOf("max", "макс"),
        ),
        PriorityRule(
            exactPackages = setOf("com.vkontakte.android"),
            packagePrefixes = setOf("com.vk.", "ru.vk."),
            labelContains = setOf("vk", "вконтакте"),
        ),
        PriorityRule(
            exactPackages = setOf("ru.ozon.app.android"),
            packagePrefixes = setOf("ru.ozon."),
            labelContains = setOf("ozon", "озон"),
        ),
        PriorityRule(
            exactPackages = setOf(
                "ru.yandex.yandexmaps",
                "ru.yandex.maps",
                "ru.yandex.yandexnavi",
                "ru.yandex.navigator",
            ),
            labelContains = setOf("yandex maps", "яндекс карты", "яндекс навигатор"),
        ),
        PriorityRule(
            packagePrefixes = setOf("ru.yandex.", "com.yandex."),
            labelContains = setOf("yandex", "яндекс"),
        ),
        PriorityRule(
            exactPackages = setOf("ru.dublgis.dgismobile"),
            labelContains = setOf("2gis", "2гис", "дубльгис"),
        ),
        PriorityRule(
            packagePrefixes = setOf("ru.gosuslugi.", "ru.rostel."),
            labelContains = setOf("госуслуги"),
        ),
        PriorityRule(
            exactPackages = setOf("ru.sberbankmobile"),
            packagePrefixes = setOf("ru.sberbank.", "ru.sberbankmobile."),
            labelContains = setOf("sber", "сбер"),
        ),
        PriorityRule(
            exactPackages = setOf("com.idamob.tinkoff.android"),
            packagePrefixes = setOf("ru.tinkoff.", "com.tinkoff."),
            labelContains = setOf("t-bank", "т-банк", "tinkoff", "тинькофф"),
        ),
        PriorityRule(
            exactPackages = setOf("ru.alfabank.mobile.android"),
            packagePrefixes = setOf("ru.alfabank."),
            labelContains = setOf("alfa", "альфа"),
        ),
        PriorityRule(
            packagePrefixes = setOf("ru.vtb."),
            labelContains = setOf("втб", "vtb"),
        ),
        PriorityRule(
            exactPackages = setOf("com.avito.android"),
            labelContains = setOf("avito", "авито"),
        ),
        PriorityRule(
            packagePrefixes = setOf("com.wildberries.", "ru.wildberries."),
            exactLabels = setOf("wb"),
            labelContains = setOf("wildberries", "вайлдберриз"),
        ),
        PriorityRule(
            packagePrefixes = setOf("ru.mvideo.", "ru.eldorado.", "ru.dns."),
            labelContains = setOf("м.видео", "m.video", "эльдорадо", "dns"),
        ),
        PriorityRule(
            packagePrefixes = setOf("ru.magnit.", "ru.x5.", "ru.pyaterochka.", "ru.perekrestok."),
            labelContains = setOf("магнит", "пятерочка", "перекресток", "perekrestok"),
        ),
        PriorityRule(
            packagePrefixes = setOf("ru.samokat.", "ru.dodo.", "ru.yandex.eda"),
            labelContains = setOf("самокат", "dodo", "додо", "яндекс еда"),
        ),
        PriorityRule(
            packagePrefixes = setOf("ru.rutube.", "ru.ok.", "ru.mail.", "ru.dzen."),
            exactLabels = setOf("ok"),
            labelContains = setOf("rutube", "рутуб", "одноклассники", "mail.ru", "dzen", "дзен"),
        ),
    )

    private data class PriorityRule(
        val exactPackages: Set<String> = emptySet(),
        val packagePrefixes: Set<String> = emptySet(),
        val exactLabels: Set<String> = emptySet(),
        val labelContains: Set<String> = emptySet(),
    ) {
        fun matches(app: InstalledApp): Boolean {
            val packageName = app.packageName.lowercase()
            val label = app.label.lowercase()
            return packageName in exactPackages ||
                exactPackages.any { packageName == it.lowercase() } ||
                packagePrefixes.any { packageName.startsWith(it) } ||
                label in exactLabels ||
                labelContains.any { label.contains(it) }
        }
    }
}
