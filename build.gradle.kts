plugins {
    base
}

allprojects {
    group = property("projectGroup") as String
    version = property("projectVersion") as String
}
