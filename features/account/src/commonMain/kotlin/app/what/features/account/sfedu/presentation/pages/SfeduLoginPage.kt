package app.what.schedule.features.insts.sfedu.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.core.Listener
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.useState
import app.what.schedule.features.insts.sfedu.domain.models.SfeduEvent
import app.what.schedule.features.insts.sfedu.domain.models.SfeduState
import app.what.schedule.ui.components.StyledTextField
import app.what.schedule.ui.theme.icons.WHATIcons
import app.what.schedule.ui.theme.icons.filled.Building

@Composable
internal fun SfeduLoginScreen(
    state: State<SfeduState>,
    listener: Listener<SfeduEvent>
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 440.dp)
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Gap(24)

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(shape = shapes.large)
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = WHATIcons.Building,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(50.dp)
            )
        }

        Gap(28)

        Text(
            text = "Кабинет студента ЮФУ",
            style = typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Gap(8)

        Text(
            text = "Введи постоянный токен из настроек БРС",
            style = typography.bodyMedium,
            color = colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Gap(4)

        Text(
            text = "grade.sfedu.ru → Настройки → Токены авторизации",
            style = typography.bodySmall,
            color = colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Gap(24)

        val (token, setToken) = useState("")
        val (name, setName) = useState("")
        val (group, setGroup) = useState("")

        StyledTextField(
            token, setToken,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "Токен авторизации или HTML профиля",
            options = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            shape = shapes.medium,
            leading = {
                Icons.Default.Key.Show(colorScheme.secondary, 20)
            }
        )

        Gap(12)

        StyledTextField(
            name, setName,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "ФИО или имя (по желанию)",
            options = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            shape = shapes.medium,
            leading = {
                Icons.Default.Person.Show(colorScheme.secondary, 20)
            }
        )

        Gap(12)

        StyledTextField(
            group, setGroup,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "Номер группы (по желанию, напр. 5 группа)",
            options = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            shape = shapes.medium,
            leading = {
                Icons.Default.Group.Show(colorScheme.secondary, 20)
            }
        )

        if (state.value.tokenError != null) {
            Gap(12)
            Text(
                state.value.tokenError!!,
                color = colorScheme.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Gap(20)

        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.medium,
            enabled = !state.value.isValidating && token.isNotBlank(),
            onClick = {
                listener(
                    SfeduEvent.TokenSubmitted(
                        token = token.trim(),
                        name = name.trim().ifBlank { null },
                        group = group.trim().ifBlank { null }
                    )
                )
            }
        ) {
            if (state.value.isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Войти")
            }
        }

        Gap(8)

        TextButton(
            onClick = { uriHandler.openUri("https://grade.sfedu.ru/") }
        ) {
            Text("Открыть grade.sfedu.ru", fontSize = 14.sp)
        }

        Gap(24)
    }
}
