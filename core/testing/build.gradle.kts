plugins {
    id("wifikill.android.library")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
    api(libs.mockk)
    api(libs.turbine)
}
