package app.what.schedule.data.remote.providers.dgtu

import app.what.data.adapters.AdaptedNewsService
import app.what.data.adapters.AdaptedScheduleService
import app.what.data.remote.CloudScheduleClient
import app.what.foundation.core.Feature
import app.what.domain.models.MetaInfo
import app.what.domain.models.SourceType
import app.what.schedule.data.remote.api.*
import app.what.schedule.dgtu.DGTUProvider
import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named

private val DGTUProviderMetadata
    get() = MetaInfo(
        id = "dgtu",
        name = "ДГТУ",
        fullName = "Донской Государственный Технический Университет",
        description = "Донской Государственный Технический Университет",
        sourceTypes = setOf(SourceType.API),
        sourceUrl = "https://edu.donstu.ru/WebApp/#/Rasp"
    )

class DGTU(
    val provider: DGTUProvider,
    val cloudClient: CloudScheduleClient? = null
) : Institution, KoinComponent {
    companion object Factory : Institution.Factory, KoinComponent {
        override val metadata by lazy { DGTUProviderMetadata }
        override fun create(): Institution {
            val client: HttpClient = get()
            val cloudClient = CloudScheduleClient(metadata.id, client)
            return DGTU(DGTUProvider(client), cloudClient)
        }
    }

    override val metadata: MetaInfo = Factory.metadata
    override val scheduleService: ScheduleService = AdaptedScheduleService(provider.scheduleClient, cloudClient)
    override val newsService: NewsService = AdaptedNewsService(provider.newsClient)
    override val accountFeature: Feature<*, *>?
        get() = getKoin().getOrNull(named("dgtuAccount"))
}
