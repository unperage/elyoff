plugins {
    id("fabric-conventions")
}

base {
    archivesName = "elyoff"
}

val fabricVersion: String by project

dependencies {
    // 唯一依赖：Fabric API（网络包与事件）。不使用任何第三方库。
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")
}
