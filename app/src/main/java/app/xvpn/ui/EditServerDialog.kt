package app.xvpn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.xvpn.data.LinkEditor
import app.xvpn.data.Profile

/**
 * Edits any kind of server: vless, trojan, shadowsocks, vmess, hysteria2, tuic, wireguard, socks5 and http proxies.
 * The common fields are fixed; everything else (transport, TLS, Reality, plugin, WireGuard address ...) is a list of
 * name/value rows that can be changed, added or removed. [onSave] returns an error text, or null when it was saved.
 */
@Composable
fun EditServerDialog(
    profile: Profile,
    fromSubscription: Boolean,
    onDismiss: () -> Unit,
    onSave: (newLink: String) -> String?,
) {
    val model = remember(profile.link) { LinkEditor.parse(profile.link) }
    if (model == null) {
        AppDialog(title = "Can't edit this server", onDismiss = onDismiss, dismissLabel = "Close") {
            Text("The editor can't read this link. Delete it and add it again.", style = Type.Small, color = C.Text)
        }
        return
    }
    val labels = LinkEditor.labels(model.protocol)
    var name by remember { mutableStateOf(model.name) }
    var host by remember { mutableStateOf(model.host) }
    var port by remember { mutableStateOf(model.port) }
    var user by remember { mutableStateOf(model.user) }
    var pass by remember { mutableStateOf(model.pass) }
    val params = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(model.params) } }
    var error by remember { mutableStateOf<String?>(null) }

    AppDialog(
        title = "Edit server", confirmLabel = "Save", onDismiss = onDismiss,
        onConfirm = {
            val (link, err) = LinkEditor.finish(
                model.copy(name = name, host = host, port = port, user = user, pass = pass, params = params.toList()),
            )
            error = err ?: link?.let { onSave(it) }
        },
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Text(
                LinkEditor.describeProtocol(model.protocol), style = Type.Small, color = C.Muted,
                modifier = Modifier.padding(bottom = Space.Md),
            )
            Labeled("Name", name) { name = it; error = null }
            Labeled("Server", host) { host = it; error = null }
            Labeled(
                "Port", port, keyboard = if (model.protocol == "hysteria2") KeyboardOptions.Default else KeyboardOptions(keyboardType = KeyboardType.Number),
            ) { port = it; error = null }
            Labeled(labels.user, user) { user = it; error = null }
            labels.pass?.let { Labeled(it, pass) { v -> pass = v; error = null } }

            Text(LinkEditor.paramsTitle(model.protocol), style = Type.SmallMedium, color = C.Text, modifier = Modifier.padding(top = Space.Sm, bottom = Space.Sm))
            params.forEachIndexed { i, (k, v) ->
                Row(Modifier.padding(bottom = Space.Sm), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(k, { params[i] = it to v; error = null }, modifier = Modifier.weight(0.42f), placeholder = "name", onCard = true)
                    Spacer(Modifier.width(Space.Sm))
                    AppTextField(v, { params[i] = k to it; error = null }, modifier = Modifier.weight(0.58f), placeholder = "value", onCard = true)
                    IconAction(ProtonIcons.Trash, "Remove", tint = C.Muted) { params.removeAt(i); error = null }
                }
            }
            TextAction("Add parameter", { params += ("" to "") })

            if (fromSubscription) Text(
                "This server comes from a subscription. Once edited it is no longer updated by it, and the next subscription update adds the original again.",
                style = Type.Caption, color = C.Muted, modifier = Modifier.padding(top = Space.Md),
            )
            error?.let { Text(it, style = Type.Caption, color = C.Red, modifier = Modifier.padding(top = Space.Md)) }
            Spacer(Modifier.height(Space.Xs))
        }
    }
}

@Composable
private fun Labeled(label: String, value: String, keyboard: KeyboardOptions = KeyboardOptions.Default, onChange: (String) -> Unit) {
    Text(label, style = Type.Caption, color = C.Muted, modifier = Modifier.padding(bottom = Space.Xs))
    AppTextField(value, onChange, onCard = true, keyboardOptions = keyboard)
    Spacer(Modifier.height(Space.Md))
}
