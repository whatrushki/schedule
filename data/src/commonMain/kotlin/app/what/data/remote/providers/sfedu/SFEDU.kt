package app.what.schedule.data.remote.providers.sfedu

import app.what.data.adapters.AdaptedNewsService
import app.what.data.adapters.AdaptedScheduleService
import app.what.foundation.core.Feature
import app.what.domain.models.MetaInfo
import app.what.domain.models.SourceType
import app.what.schedule.data.remote.api.Institution
import app.what.schedule.data.remote.api.NewsService
import app.what.schedule.data.remote.api.ScheduleService
import app.what.schedule.sfedu.SFEDUProvider
import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val SFEDUMetadata
    get() = MetaInfo(
        id = "sfedu",
        name = "ЮФУ (Мехмат)",
        fullName = "Южный федеральный университет (Институт математики, механики и компьютерных наук им. И.И. Воровича)",
        description = "ЮФУ (Мехмат) — расписание занятий и новости института",
        sourceTypes = setOf(SourceType.API, SourceType.PARSER),
        sourceUrl = "https://schedule.sfedu.ru/"
    )

class SFEDU(
    provider: SFEDUProvider
) : Institution {
    companion object Factory : Institution.Factory, KoinComponent {
        override val metadata by lazy { SFEDUMetadata }
        override fun create(): Institution {
            val client: HttpClient = get()
            return SFEDU(SFEDUProvider(client))
        }
    }

    override val metadata: MetaInfo = Factory.metadata
    override val scheduleService: ScheduleService = AdaptedScheduleService(provider.scheduleClient)
    override val newsService: NewsService = AdaptedNewsService(provider.newsClient)
    override val accountFeature: Feature<*, *>? = null
}
