dependencies {
    kapt(project(":processors:processor-spring"))
    implementation(project(":processors:processor-spring"))
    implementation("org.springframework.boot:spring-boot-starter:2.3.0.RELEASE")

    testImplementation("org.springframework.boot:spring-boot-starter-test:2.3.0.RELEASE")
}
