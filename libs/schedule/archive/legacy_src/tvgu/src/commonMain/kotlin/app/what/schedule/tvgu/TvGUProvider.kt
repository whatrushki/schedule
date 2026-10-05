package app.what.schedule.tvgu

import app.what.schedule.core.clients.InstitutionProvider
import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.InstitutionMetaDto
import app.what.schedule.core.models.SourceTypeDto
import io.ktor.client.HttpClient

class TvGUProvider(
    private val client: HttpClient,
    private val baseUrl: String = "https://timetable.tversu.ru",
    private val log: ((String) -> Unit)? = null
) : InstitutionProvider {

    override val metadata = InstitutionMetaDto(
        id = "tvgu",
        name = "ТвГУ",
        fullName = "Тверской государственный университет",
        description = "Тверской государственный университет",
        sourceTypes = setOf(SourceTypeDto.API, SourceTypeDto.PARSER),
        sourceUrl = "https://timetable.tversu.ru/"
    )

    override val scheduleClient: ScheduleClient by lazy {
        TvGUScheduleClient(client, baseUrl, log)
    }

    override val newsClient: NewsClient by lazy {
        TvGUNewsClient(client, "https://tversu.ru", log)
    }
}
