plugins {
    id("wifikill.android.library")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
