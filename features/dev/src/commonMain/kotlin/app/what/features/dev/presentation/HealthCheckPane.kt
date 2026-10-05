package app.what.schedule.features.dev.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.what.foundation.healthcheck.registry.EndpointAvailabilityCheck
import app.what.foundation.healthcheck.registry.HealthRegistry
import app.what.foundation.healthcheck.registry.NetworkConnectivityCheck
import app.what.foundation.healthcheck.ui.HealthCheckScreen
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

@Composable
fun HealthCheckPane(
    modifier: Modifier = Modifier
) {
    val httpClient: HttpClient = koinInject()

    val registry = remember(httpClient) {
        HealthRegistry().apply {
            registerAll(
                NetworkConnectivityCheck(httpClient),
                EndpointAvailabilityCheck(
                    id = "check_rksi",
                    title = "Сервер РКСИ (rksi.ru)",
                    endpointUrl = "https://rksi.ru",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_dgtu",
                    title = "API ДГТУ (edu.donstu.ru)",
                    endpointUrl = "https://edu.donstu.ru/api/Rasp/ListYears",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_sfedu",
                    title = "API ЮФУ (schedule.sfedu.ru)",
                    endpointUrl = "https://schedule.sfedu.ru/APIv1/week",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_rgups",
                    title = "Сайт РГУПС (rgups.ru)",
                    endpointUrl = "https://www.rgups.ru",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_rinh",
                    title = "API РИНХ (rasp-api.rsue.ru)",
                    endpointUrl = "https://rasp-api.rsue.ru/api/v1/schedule/search/?format=json",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_tvgu",
                    title = "API ТвГУ (timetable.tversu.ru)",
                    endpointUrl = "https://timetable.tversu.ru/api/v3/groups",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_delivery",
                    title = "Центр уведомлений (GitHub Raw)",
                    endpointUrl = "https://raw.githubusercontent.com/whatrushki/delivery/main/notifications/active.json",
                    httpClient = httpClient
                )
            )
        }
    }

    HealthCheckScreen(
        registry = registry,
        modifier = modifier.fillMaxSize()
    )
}
