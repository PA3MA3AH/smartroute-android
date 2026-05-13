package com.pa3ma3ah.smartroute

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Bitmap?
)

object InstalledAppsStore {
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return activities
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName ?: return@mapNotNull null

                if (packageName == context.packageName) {
                    return@mapNotNull null
                }

                val label = try {
                    info.loadLabel(pm)?.toString()?.trim().orEmpty()
                } catch (_: Throwable) {
                    ""
                }.ifBlank {
                    packageName
                }

                val icon = try {
                    drawableToBitmap(info.loadIcon(pm))
                } catch (_: Throwable) {
                    null
                }

                InstalledApp(
                    label = label,
                    packageName = packageName,
                    icon = icon
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<InstalledApp> { it.label.lowercase() }
                    .thenBy { it.packageName }
            )
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }
}