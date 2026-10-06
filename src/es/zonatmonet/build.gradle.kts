import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZonaTMO.net"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    // 1.4 keeps the extension installable on older Suwayomi/Tachimanga builds.
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://zonatmo.net"
    }

    deeplink {
        host("zonatmo.net")
        host("www.zonatmo.net")
        path("/manga/..*")
    }
}
