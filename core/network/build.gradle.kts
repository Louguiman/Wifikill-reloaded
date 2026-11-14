plugins {
    id("wifikill.android.library")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
