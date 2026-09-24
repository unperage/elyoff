plugins {
    id("fabric-conventions")
}

base {
    archivesName = "elyoff"
}

val fabricVersion: String by project
val modmenuVersion: String by project

repositories {
    // ModMenu（仅编译期使用，运行期是可选依赖）
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/releases/")
    }
}

dependencies {
    // 运行期唯一依赖：Fabric API（网络包与事件）。不使用任何第三方库。
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")

    // ModMenu 只在装了它的客户端上被加载，用来提供图形设置界面。
    // 用 compileOnly：不进 jar、不成为运行期依赖。
    modCompileOnly("com.terraformersmc:modmenu:${modmenuVersion}")
}
