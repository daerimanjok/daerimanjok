package app.daerimanjok.feature.daeri.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.daerimanjok.R
import app.daerimanjok.feature.daeri.OwnerSetup
import app.daerimanjok.ui.components.ContentPadding
import app.daerimanjok.ui.components.DmButton
import app.daerimanjok.ui.components.DmButtonKind
import app.daerimanjok.ui.components.DmDivider
import app.daerimanjok.ui.components.DmIcons
import app.daerimanjok.ui.components.IconLine
import app.daerimanjok.ui.components.IconTile
import app.daerimanjok.ui.components.Panel
import app.daerimanjok.ui.components.TopBar
import app.daerimanjok.ui.theme.Dm

/** S1 대리 호출: confirm today's destination, then start driver mode. */
@Composable
fun CallScreen(
    setup: OwnerSetup,
    onStart: () -> Unit,
    onChangeDestination: () -> Unit,
    onEditSetup: () -> Unit,
) {
    val dest = setup.destination ?: return
    val destLabel = dest.label()
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.call_bar), onBack = onEditSetup)
        // The card sits next to the question rather than against the screen edge (prototype layout).
        Row(
            Modifier.fillMaxSize().padding(ContentPadding).padding(end = 98.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.call_headline, withSubjectParticle(destLabel)), style = Dm.type.hXl, color = Dm.colors.t1)
                Spacer(Modifier.height(48.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconTile(DmIcons.Home, size = 104.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(destLabel, style = Dm.type.title, color = Dm.colors.t1)
                        Text(dest.address, style = Dm.type.bodyL, color = Dm.colors.t2, maxLines = 1)
                    }
                }
                DmDivider(Modifier.padding(vertical = 32.dp).widthIn(max = 860.dp))
                if (setup.parkingSpot.isNotBlank()) {
                    val parking = buildAnnotatedString {
                        append(stringResource(R.string.call_parking))
                        append("  ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Dm.colors.t1)) { append(setup.parkingSpot) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(DmIcons.Parking, null, tint = Dm.colors.t2, modifier = Modifier.size(32.dp))
                        Text(parking, style = Dm.type.bodyL, color = Dm.colors.t2)
                    }
                }
                if (setup.note.isNotBlank()) {
                    IconLine(DmIcons.Info, setup.note, Modifier.padding(top = 16.dp))
                }
                Spacer(Modifier.height(64.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DmButton(stringResource(R.string.call_start), onStart, parkedOnly = true)
                    DmButton(stringResource(R.string.call_change), onChangeDestination, kind = DmButtonKind.Basic, parkedOnly = true)
                }
            }
            Panel(Modifier.width(630.dp), padding = PaddingValues(56.dp)) {
                Text(stringResource(R.string.call_panel_title), style = Dm.type.titleS, color = Dm.colors.t1)
                Spacer(Modifier.height(40.dp))
                Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
                    Benefit(DmIcons.Guide, stringResource(R.string.call_benefit_guide))
                    Benefit(DmIcons.Pin, stringResource(R.string.call_benefit_route))
                    Benefit(DmIcons.Lock, stringResource(R.string.call_benefit_lock))
                }
            }
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Icon(icon, null, tint = Dm.colors.accentText, modifier = Modifier.size(44.dp))
        Text(text, style = Dm.type.bodyL.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t1)
    }
}
