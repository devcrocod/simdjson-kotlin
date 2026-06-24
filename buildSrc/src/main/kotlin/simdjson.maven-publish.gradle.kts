plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    if (providers.gradleProperty("signing.keyId").isPresent ||
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
    ) {
        signAllPublications()
    }

    coordinates(group.toString(), project.name, version.toString())

    pom {
        name = project.name
        description = "Kotlin Multiplatform JSON parser based on simdjson"
        url = "https://github.com/devcrocod/simdjson-kotlin"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "devcrocod"
                name = "Pavel Gorgulov"
                url = "https://github.com/devcrocod"
            }
        }
        scm {
            url = "https://github.com/devcrocod/simdjson-kotlin"
            connection = "scm:git:git://github.com/devcrocod/simdjson-kotlin.git"
            developerConnection = "scm:git:ssh://git@github.com/devcrocod/simdjson-kotlin.git"
        }
    }
}
