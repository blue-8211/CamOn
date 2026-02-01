pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // 경로 우선순위 강제
    repositories {
        google()
        mavenCentral() // 👈 이 라이브러리는 무조건 여기에 있습니다.
    }
}
rootProject.name = "CamOn"
include(":app")