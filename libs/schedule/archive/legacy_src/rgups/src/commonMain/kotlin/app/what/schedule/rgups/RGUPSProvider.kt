package app.what.schedule.rgups

import app.what.schedule.core.cache.FileCache
import app.what.schedule.core.cache.NoOpFileCache
import app.what.schedule.core.clients.InstitutionProvider
import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.InstitutionMetaDto
import app.what.schedule.core.models.SourceTypeDto
import io.ktor.client.HttpClient

class RGUPSProvider(
    client: HttpClient,
    fileCache: FileCache = NoOpFileCache(),
    log: ((String) -> Unit)? = null
) : InstitutionProvider {

    override val metadata: InstitutionMetaDto = InstitutionMetaDto(
        id = "rgups",
        name = "РГУПС",
        fullName = "Ростовский государственный университет путей сообщения",
        description = "Ростовский государственный университет путей сообщения (г. Ростов-на-Дону)",
        sourceTypes = setOf(SourceTypeDto.PARSER),
        sourceUrl = "https://www.rgups.ru/services/time/",
        hasAccountService = false
    )

    override val scheduleClient: ScheduleClient = RGUPSScheduleClient(
        client = client,
        fileCache = fileCache,
        log = log
    )

    override val newsClient: NewsClient = RGUPSNewsClient(
        client = client,
        log = log
    )
}
