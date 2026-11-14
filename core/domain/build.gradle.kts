plugins {
    id("wifikill.kotlin.library")
}

dependencies {
    api(project(":core:common"))
    api(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
