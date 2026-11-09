package crow.evilo.feature.library

import crow.evilo.core.model.Manga

data class LibraryUiState(
    val manga: List<Manga> = emptyList(),
)
