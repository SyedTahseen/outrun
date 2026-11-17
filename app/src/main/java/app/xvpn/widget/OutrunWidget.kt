package app.xvpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.xvpn.MainActivity
import app.xvpn.R
import app.xvpn.data.LinkParser
import app.xvpn.data.Store
import app.xvpn.ui.C
import app.xvpn.vpn.OutrunVpnService
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState

/** Home-screen widget: shows the VPN state and server, and has a power button to connect / disconnect. */
class OutrunWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, build(context, manager, it)) }
    }

    /** Called by the launcher while the widget is being resized, so the layout can adapt to the new size. */
    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        manager.updateAppWidget(appWidgetId, build(context, manager, appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) toggle(context.applicationContext)
    }

    companion object {
        const val ACTION_TOGGLE = "app.xvpn.widget.TOGGLE"

        // The widget art is always dark (own design), so it uses the dark values of the app palette regardless of the system theme.
        private val GREEN = 0xFF2CFFCC.toInt()   // vpnGreen (dark)
        private val AMBER = 0xFFFFB84D.toInt()   // notificationWarning (dark)
        private val WHITE = Color.WHITE

        /** Re-draws every placed widget from the current [VpnState]. Safe to call from any thread. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, OutrunWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { manager.updateAppWidget(it, build(app, manager, it)) }
        }

        private fun build(ctx: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
            // Current size of this widget in dp (portrait min-height is the safe lower bound).
            val heightDp = manager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val compact = heightDp in 1..64
            val status = VpnState.status.value
            val link = VpnState.activeLink.value ?: Store(ctx).selected
            val profile = link?.let { LinkParser.parse(it) }

            val title = when (status) {
                Status.Connected -> ctx.getString(R.string.widget_title_on)
                Status.Connecting, Status.WaitingForNetwork -> ctx.getString(R.string.widget_title_connecting)
                Status.Disconnected -> ctx.getString(R.string.widget_title_off)
            }
            val sub = when {
                profile == null -> ctx.getString(R.string.widget_sub_choose_server)
                status == Status.Connecting -> ctx.getString(R.string.widget_sub_connecting)
                status == Status.WaitingForNetwork -> ctx.getString(R.string.widget_sub_waiting_network)
                status == Status.Disconnected -> profile.name
                else -> {
                    val host = if (':' in profile.host) "[${profile.host}]" else profile.host
                    "$host:${profile.port}"
                }
            }
            val views = RemoteViews(ctx.packageName, R.layout.widget_outrun)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_sub, sub)
            // Very short (1-row) widgets: hide the server line so nothing is clipped.
            views.setViewVisibility(R.id.widget_sub, if (compact) View.GONE else View.VISIBLE)
            views.setImageViewResource(
                R.id.widget_art,
                if (status == Status.Disconnected) R.drawable.widget_art_off else R.drawable.widget_art_on,
            )
            views.setInt(
                R.id.widget_power, "setColorFilter",
                when (status) { Status.Connected -> GREEN; Status.Connecting, Status.WaitingForNetwork -> AMBER; Status.Disconnected -> WHITE },
            )

            views.setOnClickPendingIntent(R.id.widget_root, openApp(ctx, 0))
            // Without a server or the system VPN permission the app has to be opened first.
            val needsApp = status == Status.Disconnected && (profile == null || VpnService.prepare(ctx) != null)
            views.setOnClickPendingIntent(
                R.id.widget_power,
                if (needsApp) openApp(ctx, 1) else PendingIntent.getBroadcast(
                    ctx, 2,
                    Intent(ctx, OutrunWidget::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }

        private fun openApp(ctx: Context, req: Int) = PendingIntent.getActivity(
            ctx, req,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun toggle(ctx: Context) {
            runCatching {
                if (VpnState.status.value != Status.Disconnected) {
                    OutrunVpnService.stop(ctx)
                } else {
                    val link = Store(ctx).selected
                    if (link == null || VpnService.prepare(ctx) != null) {
                        ctx.startActivity(
                            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else {
                        OutrunVpnService.start(ctx, link)
                    }
                }
            }
            refresh(ctx)
        }
    }
}
