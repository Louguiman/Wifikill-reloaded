plugins {
    id("wifikill.android.library")
}

dependencies {
    api(project(":integrations:router-api"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
}
