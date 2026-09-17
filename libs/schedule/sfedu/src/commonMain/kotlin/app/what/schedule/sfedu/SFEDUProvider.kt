package app.what.schedule.sfedu

import app.what.schedule.core.clients.InstitutionProvider
import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.InstitutionMetaDto
import app.what.schedule.core.models.SourceTypeDto
import io.ktor.client.HttpClient

class SFEDUProvider(
    private val client: HttpClient,
    private val scheduleBaseUrl: String = "https://schedule.sfedu.ru",
    private val newsBaseUrl: String = "https://mmcs.sfedu.ru",
    private val log: ((String) -> Unit)? = null
) : InstitutionProvider {

    override val metadata = InstitutionMetaDto(
        id = "sfedu",
        name = "ЮФУ (Мехмат)",
        fullName = "Южный федеральный университет (Институт математики, механики и компьютерных наук им. И.И. Воровича)",
        description = "ЮФУ (Мехмат) — расписание занятий и новости института",
        sourceTypes = setOf(SourceTypeDto.API, SourceTypeDto.PARSER),
        sourceUrl = "https://schedule.sfedu.ru/"
    )

    override val scheduleClient: ScheduleClient by lazy {
        SFEDUScheduleClient(client, scheduleBaseUrl, log)
    }

    override val newsClient: NewsClient by lazy {
        SFEDUNewsClient(client, newsBaseUrl, log)
    }
}
