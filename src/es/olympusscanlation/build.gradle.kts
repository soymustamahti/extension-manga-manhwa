import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Olympus Scanlation"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    // 1.4 keeps the extension installable on older Suwayomi/Tachimanga builds.
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl {
            custom("https://olympusxyz.com")
        }
        versionId = 3
    }
}
