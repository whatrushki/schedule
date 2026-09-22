package app.what.schedule.core.models

import kotlinx.datetime.LocalDate

data class NewListItemDto(
    val id: String,
    val title: String,
    val description: String,
    val date: LocalDate,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val tags: List<String> = emptyList()
)

data class AuthorInfoDto(
    val avatarUrl: String? = null,
    val name: String = "",
    val role: String = ""
)

sealed interface NewContentBlockDto {
    data class Text(val html: String) : NewContentBlockDto
    data class Subtitle(val text: String) : NewContentBlockDto
    data class Image(val url: String) : NewContentBlockDto
    data class ImageCarousel(val urls: List<String>) : NewContentBlockDto
    data class UnsortedList(val items: List<String>) : NewContentBlockDto
    data class SortedList(val items: List<String>) : NewContentBlockDto
    data class Quote(
        val author: AuthorInfoDto,
        val text: String
    ) : NewContentBlockDto
    data class Info(val text: String) : NewContentBlockDto
    data class VideoVK(val url: String) : NewContentBlockDto
}

data class NewDetailDto(
    val id: String,
    val title: String,
    val fullText: String = "",
    val descriptionHtml: String? = null,
    val date: LocalDate,
    val bannerUrl: String? = null,
    val images: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val contentBlocks: List<NewContentBlockDto> = emptyList(),
    val tags: List<String> = emptyList()
)
