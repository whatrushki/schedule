package app.what.schedule.data.remote.providers.rgups_tuapse

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
import app.what.schedule.rgups_tuapse.RGUPSTuapseProvider
import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val RGUPSTuapseMetadata
    get() = MetaInfo(
        id = "rgups_tuapse",
        name = "РГУПС (Туапсе)",
        fullName = "Филиал РГУПС в г. Туапсе",
        description = "Филиал Ростовского государственного университета путей сообщения в г. Туапсе",
        sourceTypes = setOf(SourceType.API, SourceType.PARSER),
        sourceUrl = "https://rgups-tuapse.ru/s-raspisanie"
    )

class RGUPSTuapse(
    provider: RGUPSTuapseProvider,
    cloudClient: CloudScheduleClient? = null
) : Institution {
    companion object Factory : Institution.Factory, KoinComponent {
        override val metadata by lazy { RGUPSTuapseMetadata }
        override fun create(): Institution {
            val client: HttpClient = get()
            val fileCache: FileCache = get()
            val cloudClient = CloudScheduleClient(metadata.id, client)
            return RGUPSTuapse(RGUPSTuapseProvider(client, fileCache), cloudClient)
        }
    }

    override val metadata: MetaInfo = Factory.metadata
    override val scheduleService: ScheduleService = AdaptedScheduleService(provider.scheduleClient, cloudClient)
    override val newsService: NewsService = AdaptedNewsService(provider.newsClient)
    override val accountFeature: Feature<*, *>? = null
}
