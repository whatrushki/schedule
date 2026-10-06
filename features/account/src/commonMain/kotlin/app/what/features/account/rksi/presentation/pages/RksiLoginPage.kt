package app.what.schedule.features.insts.rksi.presentation.pages

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.domain.models.RksiState
import app.what.schedule.ui.components.StyledTextField
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.Building
import app.what.foundation.ui.icons.filled.Features

@Composable
internal fun RksiLoginScreen(
    state: State<RksiState>,
    listener: Listener<RksiEvent>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 440.dp)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(shape = shapes.large)
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = WHATIcons.Building,
                contentDescription = "App icon",
                tint = colorScheme.primary,
                modifier = Modifier.size(54.dp)
            )
        }

        Gap(40)

        Text(
            text = "Кабинет студента РКСИ",
            style = typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Gap(8)

        Text(
            text = "Введи логин и пароль от rksi.ru/account",
            style = typography.bodyLarge,
            color = colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Gap(20)

        val (login, setLogin) = useState("")
        val (password, setPass) = useState("")

        StyledTextField(
            login, setLogin,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "Логин или Email",
            options = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = shapes.medium,
            leading = {
                WHATIcons.Features.Show(colorScheme.secondary, 22)
            }
        )

        Gap(12)

        StyledTextField(
            password, setPass,
            modifier = Modifier.fillMaxWidth(),
            debounce = 500,
            placeholder = "Пароль",
            options = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            shape = shapes.medium,
            leading = {
                WHATIcons.Features.Show(colorScheme.secondary, 22)
            }
        )

        if (state.value.loginError != null) {
            Gap(12)
            Text(
                state.value.loginError!!,
                color = colorScheme.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Gap(20)

        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.medium,
            enabled = !state.value.isLoggingIn && login.isNotBlank() && password.isNotBlank(),
            onClick = {
                listener(RksiEvent.AuthClicked(login.trim(), password.trim()))
            }
        ) {
            if (state.value.isLoggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Войти")
            }
        }
    }
}
