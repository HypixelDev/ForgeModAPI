# Forge Mod API

This repository contains the implementation of the Hypixel Mod API for the Forge Mod Loader in 1.8.9 for end-users. If you are a developer, and are looking to utilise the API you should look at the core [Hypixel Mod API](https://github.com/HypixelDev/ModAPI) repository.

## Usage

Add the HyPixel Maven repository to your build:

```kotlin
repositories {
    maven("https://repo.hypixel.net/repository/Hypixel/")
}
```

Then depend on the Forge Mod API. This will automatically pull in dependencies too.

```kotlin
val version = "1.0.0.2"
dependencies {
    modImplementation("net.hypixel:mod-api-forge:$version")
    // If you use ForgeGradle 2 you might need to use fg.deobf or deobfCompile instead. Consult your MDK for tips on how
    // to depend on an obfuscated dependency
}
```

From here on out you can use the [HyPixel Mod API](https://github.com/HypixelDev/ModAPI#example-usage) directly.

### Bundling the HyPixel Mod API

When using the HyPixel Mod API you need to instruct your users to
[download](https://modrinth.com/mod/hypixel-mod-api/versions?l=forge) the mod api and put it in their mods folders.

Alternatively you can bundle a loading tweaker instead. This involves a bit more setup, but will result in your mod
containing a copy of the mod api and at runtime selecting the newest available version of the mod api.

First you need to have a shadow plugin in your gradle setup. Note that normal `fileTree` based JAR copying does not
work, since we will need to relocate some files. Instead, use the [shadow plugin](https://github.com/johnrengelman/shadow)
or make use of a [template](https://github.com/nea89o/Forge1.8.9Template) with the plugin already set up.

Once you have your shadow plugin set up you will need to include a new dependency:
```kotlin
dependencies {
    modImplementation("net.hypixel:mod-api-forge:$version") // You should already have this dependency from earlier
    shadowImpl("net.hypixel:mod-api-forge-tweaker:$version") // You need to add this dependency
}
```

Make sure to relocate the `net.hypixel.modapi.tweaker` package to a unique location such as `my.modid.modapitweaker`.

Finally, add a manifest entry to your JAR pointing the `TweakClass` to `my.modid.modapitweaker.HypixelModAPITweaker`
(or otherwise load the tweaker using delegation).

```kotlin
tasks.shadowJar {
	configurations = listOf(shadowImpl)
    relocate("net.hypixel.modapi.tweaker", "my.modid.modapitweaker.HypixelModAPITweaker")
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
	manifest.attributes.run {
		this["TweakClass"] = "my.modid.modapitweaker.HypixelModAPITweaker"
	}
}
```

Now your users will automatically use the bundled version of the mod api.


## Contributing

If you wish to contribute to this implementation of the Mod API to offer improvements or fixes, you can do so by forking the repository and creating a pull request.

> [!IMPORTANT]  
> Run `gradlew setupDecompWorkspace` after importing the project to generate the files required to build it

If IntelliJ still shows unknown symbol errors after running the command, you may need to [clear the filesystem cache](https://www.jetbrains.com/help/idea/invalidate-caches.html).

### Testing Your Changes

You can enable DevAuth to log in to the Hypixel server with your Minecraft account. Please see the [DevAuth docs](https://github.com/DJtheRedstoner/DevAuth) for more details.