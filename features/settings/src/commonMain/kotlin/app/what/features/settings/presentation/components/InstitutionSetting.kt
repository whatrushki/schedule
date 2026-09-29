package app.what.schedule.features.settings.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.what.foundation.data.settings.PreferenceStorage
import app.what.foundation.data.settings.types.asDialog
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.bclick
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.schedule.data.remote.api.insts

fun PreferenceStorage.Value<String>.asInstitutionChoice(
    sideEffect: (String?) -> Unit
) = asDialog { value, set ->
    val dialog = rememberDialogController()
    val selected by value.collect()
    
    Column(Modifier.padding(8.dp)) {
        insts.forEach {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .bclick {
                        set(it.metadata.id)
                        dialog.close()
                        sideEffect(it.metadata.id)
                    }
                    .padding(12.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == it.metadata.id, onClick = null)
                Gap(12)
                Text(it.metadata.name)
            }
        }
    }
}