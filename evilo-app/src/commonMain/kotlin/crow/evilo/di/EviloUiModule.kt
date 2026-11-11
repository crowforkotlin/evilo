package crow.evilo.di

import coil3.PlatformContext
import crow.evilo.app.EviloImages
import org.koin.dsl.module

internal fun eviloUiModule(context: PlatformContext) = module {
    single { EviloImages(context, get()) }
}
