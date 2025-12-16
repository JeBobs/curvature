plugins {
    id("java")
}

group = "cc.interstellarmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "helpchatRepoReleases"
        url = uri("https://repo.helpch.at/releases")
    }

}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("apiJar") {
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include(
            "cc/interstellarmc/curvature/CurvatureService.class",
            "cc/interstellarmc/curvature/CurvatureServiceImpl.class",
            "cc/interstellarmc/curvature/CurvatureAPI.class",
            "cc/interstellarmc/curvature/ICurve.class"
        )
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}