version = 1

cloudstream {
    language = "all"
    description = "Telebox/Linkbox provider for CloudStream - Access your cloud storage files"
    authors = listOf("yacinezero")

    status = 1 // 0: Down, 1: Ok, 2: Slow, 3: Beta only

    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "OVA"
    )

    iconUrl = "https://www.linkbox.to/favicon.ico"
}

android {
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("com.lagradost:cloudstream3:pre-release")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jsoup:jsoup:1.16.1")
}
