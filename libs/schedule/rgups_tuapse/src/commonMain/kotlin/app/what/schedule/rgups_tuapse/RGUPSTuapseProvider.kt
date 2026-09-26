package app.what.schedule.rgups_tuapse

import app.what.schedule.core.cache.FileCache
import app.what.schedule.core.cache.NoOpFileCache
import app.what.schedule.core.clients.InstitutionProvider
import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.InstitutionMetaDto
import app.what.schedule.core.models.SourceTypeDto
import io.ktor.client.HttpClient

class RGUPSTuapseProvider(
    client: HttpClient,
    fileCache: FileCache = NoOpFileCache(),
    log: ((String) -> Unit)? = null
) : InstitutionProvider {

    override val metadata: InstitutionMetaDto = InstitutionMetaDto(
        id = "rgups_tuapse",
        name = "РГУПС (Туапсе)",
        fullName = "Филиал РГУПС в г. Туапсе",
        description = "Филиал Ростовского государственного университета путей сообщения в г. Туапсе",
        sourceTypes = setOf(SourceTypeDto.API, SourceTypeDto.PARSER),
        sourceUrl = "https://rgups-tuapse.ru/s-raspisanie",
        hasAccountService = false
    )

    override val scheduleClient: ScheduleClient = RGUPSTuapseScheduleClient(
        client = client,
        fileCache = fileCache,
        log = log
    )

    override val newsClient: NewsClient = RGUPSTuapseNewsClient(
        client = client,
        log = log
    )
}
