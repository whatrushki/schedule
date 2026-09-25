package app.what.schedule.data.remote.providers.rksi

import app.what.data.adapters.AdaptedNewsService
import app.what.data.adapters.AdaptedScheduleService
import app.what.data.remote.CloudScheduleClient
import app.what.schedule.core.cache.FileCache
import app.what.domain.models.MetaInfo
import app.what.domain.models.SourceType
import app.what.foundation.core.Feature
import app.what.schedule.data.remote.api.*
import app.what.schedule.rksi.RKSIProvider
import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val RKSIMetadata
    get() = MetaInfo(
        id = "rksi",
        name = "РКСИ",
        fullName = "Ростовский-на-Дону Колледж Связи и Информатики",
        description = "Ростовский-на-Дону Колледж Связи и Информатики",
        sourceTypes = setOf(SourceType.PARSER, SourceType.EXCEL),
        sourceUrl = "https://rksi.ru/mobile_schedule"
    )

class RKSI(
    provider: RKSIProvider,
    cloudClient: CloudScheduleClient? = null
) : Institution {
    companion object Factory : Institution.Factory, KoinComponent {
        override fun create(): Institution {
            val client: HttpClient = get()
            val fileCache: FileCache = get()
            val xlsxReader = getKoin().getOrNull<app.what.schedule.rksi.parser.XlsxReader>()
            val provider = RKSIProvider(client, fileCache, xlsxReader = xlsxReader)
            val cloudClient = CloudScheduleClient(metadata.id, client)
            return RKSI(provider, cloudClient)
        }
        override val metadata: MetaInfo by lazy { RKSIMetadata }
    }

    override val metadata: MetaInfo = Factory.metadata
    override val scheduleService: ScheduleService = AdaptedScheduleService(provider.scheduleClient, cloudClient)
    override val newsService: NewsService = AdaptedNewsService(provider.newsClient)
    override val accountFeature: Feature<*, *>? = null
}