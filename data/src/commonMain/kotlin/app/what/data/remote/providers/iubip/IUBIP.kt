package app.what.schedule.data.remote.providers.iubip

import app.what.data.adapters.AdaptedNewsService
import app.what.data.adapters.AdaptedScheduleService
import app.what.data.remote.CloudScheduleClient
import app.what.foundation.core.Feature
import app.what.domain.models.MetaInfo
import app.what.domain.models.SourceType
import app.what.schedule.data.remote.api.Institution
import app.what.schedule.data.remote.api.NewsService
import app.what.schedule.data.remote.api.ScheduleService
import app.what.schedule.iubip.IUBIPProvider
import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val IUBIPMetadata
    get() = MetaInfo(
        id = "iubip",
        name = "ИУБиП",
        fullName = "Южный Университет (Институт Управления, Бизнеса и Права)",
        description = "Южный Университет (Институт Управления, Бизнеса и Права)",
        sourceTypes = setOf(SourceType.API, SourceType.PARSER),
        sourceUrl = "https://iubip.ru/schedule/"
    )

class IUBIP(
    provider: IUBIPProvider,
    cloudClient: CloudScheduleClient? = null
) : Institution {
    companion object Factory : Institution.Factory, KoinComponent {
        override val metadata by lazy { IUBIPMetadata }
        override fun create(): Institution {
            val client: HttpClient = get()
            val cloudClient = CloudScheduleClient(metadata.id, client)
            return IUBIP(IUBIPProvider(client), cloudClient)
        }
    }

    override val metadata: MetaInfo = Factory.metadata
    override val scheduleService: ScheduleService = AdaptedScheduleService(provider.scheduleClient, cloudClient)
    override val newsService: NewsService = AdaptedNewsService(provider.newsClient)
    override val accountFeature: Feature<*, *>? = null
}