package app.what.schedule.data.remote.providers.rinh

import app.what.data.adapters.AdaptedNewsService
import app.what.data.adapters.AdaptedScheduleService
import app.what.data.remote.CloudScheduleClient
import app.what.foundation.core.Feature
import app.what.domain.models.MetaInfo
import app.what.domain.models.SourceType
import app.what.schedule.data.remote.api.Institution
import app.what.schedule.data.remote.api.NewsService
import app.what.schedule.data.remote.api.ScheduleService
import app.what.schedule.rinh.RINHProvider
import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val RINHMetadata
    get() = MetaInfo(
        id = "rinh",
        name = "РИНХ",
        fullName = "Ростовский Государственный Экономический Университет",
        description = "Ростовский Государственный Экономический Университет",
        sourceTypes = setOf(SourceType.API),
        sourceUrl = "https://rasp.rsue.ru"
    )

class RINH(
    provider: RINHProvider,
    cloudClient: CloudScheduleClient? = null
) : Institution {
    companion object Factory : Institution.Factory, KoinComponent {
        override val metadata by lazy { RINHMetadata }
        override fun create(): Institution {
            val client: HttpClient = get()
            val cloudClient = CloudScheduleClient(metadata.id, client)
            return RINH(RINHProvider(client), cloudClient)
        }
    }

    override val metadata: MetaInfo = Factory.metadata
    override val scheduleService: ScheduleService = AdaptedScheduleService(provider.scheduleClient, cloudClient)
    override val newsService: NewsService = AdaptedNewsService(provider.newsClient)
    override val accountFeature: Feature<*, *>? = null
}
