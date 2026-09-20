plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf") version "0.9.4"
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("io.grpc:grpc-bom:${libs.versions.grpc.get()}")
        mavenBom("com.google.protobuf:protobuf-bom:${libs.versions.protobuf.get()}")
    }
}

dependencies {
    implementation(project(":services:shared:shared-web"))
    implementation(project(":services:shared:shared-logback"))
    implementation(project(":services:shared:shared-observability"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE") {
        exclude(group = "io.grpc", module = "grpc-netty-shaded")
    }
    implementation("io.grpc:grpc-netty")
    implementation("com.google.protobuf:protobuf-java")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        proto {
            srcDir(rootProject.file("proto"))
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                create("grpc") { }
            }
        }
    }
}
