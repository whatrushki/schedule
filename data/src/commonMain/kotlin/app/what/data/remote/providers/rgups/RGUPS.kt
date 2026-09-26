package app.what.schedule.data.remote.providers.rgups

import app.what.data.adapters.AdaptedNewsService
import app.what.data.adapters.AdaptedScheduleService
import app.what.data.remote.CloudScheduleClient
import app.what.domain.models.MetaInfo
import app.what.domain.models.SourceType
import app.what.foundation.core.Feature
import app.what.schedule.core.cache.FileCache
import app.what.schedule.data.remote.api.Institution
import app.what.schedule.data.remote.api.NewsService
import app.what.schedule.data.remote.api.ScheduleService
import app.what.schedule.rgups.RGUPSProvider
import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val RGUPSMetadata
    get() = MetaInfo(
        id = "rgups",
        name = "РГУПС",
        fullName = "Ростовский государственный университет путей сообщения",
        description = "Ростовский государственный университет путей сообщения (г. Ростов-на-Дону)",
        sourceTypes = setOf(SourceType.PARSER),
        sourceUrl = "https://www.rgups.ru/services/time/"
    )

class RGUPS(
    provider: RGUPSProvider,
    cloudClient: CloudScheduleClient? = null
) : Institution {
    companion object Factory : Institution.Factory, KoinComponent {
        override val metadata by lazy { RGUPSMetadata }
        override fun create(): Institution {
            val client: HttpClient = get()
            val fileCache: FileCache = get()
            val cloudClient = CloudScheduleClient(metadata.id, client)
            return RGUPS(RGUPSProvider(client, fileCache), cloudClient)
        }
    }

    override val metadata: MetaInfo = Factory.metadata
    override val scheduleService: ScheduleService = AdaptedScheduleService(provider.scheduleClient, cloudClient)
    override val newsService: NewsService = AdaptedNewsService(provider.newsClient)
    override val accountFeature: Feature<*, *>? = null
}
