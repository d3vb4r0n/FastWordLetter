plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.swing")
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    implementation("fr.opensagres.xdocreport:fr.opensagres.poi.xwpf.converter.core:2.0.4")
    implementation("fr.opensagres.xdocreport:fr.opensagres.poi.xwpf.converter.pdf:2.0.4")
    implementation("com.lowagie:itext:2.1.7")

    implementation("org.apache.pdfbox:pdfbox:2.0.31")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.example.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}