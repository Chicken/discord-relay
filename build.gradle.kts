import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id( "fabric-loom" )
	kotlin( "jvm" ) version( System.getProperty( "kotlin_version" ) )
	kotlin( "plugin.serialization" ) version( System.getProperty( "kotlin_version" ) )
	id( "com.gradleup.shadow" ) version "9.3.2"
}

base {
	archivesName.set( project.extra[ "archives_base_name" ] as String )
}
version = project.extra[ "mod_version" ] as String
group = project.extra[ "maven_group" ] as String

val shadowImpl by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
}

configurations {
	getByName( "implementation" ).extendsFrom( shadowImpl )
}

repositories {
	maven {
		url = uri( "https://api.modrinth.com/maven" )
	}
	mavenCentral()
}

dependencies {

	// Minecraft
	minecraft( "com.mojang:minecraft:${ project.extra[ "minecraft_version" ] }" )

	// Minecraft source mappings - https://github.com/FabricMC/yarn
	mappings( "net.fabricmc:yarn:${ project.extra[ "yarn_mappings" ] }:v2" )

	// Fabric Loader - https://github.com/FabricMC/fabric-loader
	modImplementation( "net.fabricmc:fabric-loader:${ project.extra[ "loader_version" ] }" )

	// Fabric API - https://github.com/FabricMC/fabric
	modImplementation( "net.fabricmc.fabric-api:fabric-api:${ project.extra[ "fabric_version" ] }" )

	// Kotlin support for Fabric - https://github.com/FabricMC/fabric-language-kotlin
	modImplementation( "net.fabricmc:fabric-language-kotlin:${ project.extra[ "fabric_language_kotlin_version" ] }" )

	// Kotlin JSON serialization (for config files)
	implementation( "org.jetbrains.kotlinx:kotlinx-serialization-json:${ project.extra[ "kotlinx_serialization_json_version" ] }" )

	// Kord - Discord library for Kotlin (shaded into mod jar)
	shadowImpl( "dev.kord:kord-core:${ project.extra[ "kord_version" ] }" ) {
		// Exclude Kotlin stdlib and coroutines - provided by Fabric Language Kotlin
		exclude( group = "org.jetbrains.kotlin" )
		exclude( group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core" )
		exclude( group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm" )
		exclude( group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8" )
		// Keep kotlinx-serialization as Kord needs it internally and our version may differ
	}

	// Vanish
	modImplementation( "maven.modrinth:vanish:${ project.extra[ "vanish_version" ] }" )
}

tasks {
	val javaVersion = JavaVersion.toVersion( ( project.extra[ "java_version" ] as String ).toInt() )

	withType<JavaCompile> {
		options.encoding = "UTF-8"
		sourceCompatibility = javaVersion.toString()
		targetCompatibility = javaVersion.toString()
		options.release.set( javaVersion.toString().toInt() )
	}

	withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
		 compilerOptions {
		 	jvmTarget.set(JvmTarget.fromTarget(project.extra[ "java_version" ] as String))
		 }
	}

	shadowJar {
		configurations = listOf( shadowImpl )
		archiveClassifier.set( "dev-shadow" )
		// Exclude slf4j
		exclude( "org/slf4j/**" )
		// Exclude Kotlin stdlib and runtime (provided by Fabric Language Kotlin)
		exclude( "kotlin/**" )
		exclude( "META-INF/kotlin-stdlib*" )
		exclude( "META-INF/proguard/**" )
		// Relocate shaded libraries to avoid conflicts with other mods
		relocate( "dev.kord", "com.viral32111.discordrelay.shadow.kord" )
		relocate( "io.ktor", "com.viral32111.discordrelay.shadow.ktor" )
		//relocate( "kotlinx", "com.viral32111.discordrelay.shadow.kotlinx" )
		relocate( "okhttp3", "com.viral32111.discordrelay.shadow.okhttp3" )
		relocate( "io.github", "com.viral32111.discordrelay.shadow.github" )
		relocate( "okio", "com.viral32111.discordrelay.shadow.okio" )
	}

	remapJar {
		input.set( shadowJar.flatMap { it.archiveFile } )
		dependsOn( shadowJar )
		archiveClassifier.set( "" )
	}

	jar {
		from( "LICENSE.txt" ) {
			rename { "${ it }_${ base.archivesName.get() }.txt" }
		}
	}

	processResources {

		// Metadata
		filesMatching( "fabric.mod.json" ) {
			expand( mutableMapOf(
				"version" to project.extra[ "mod_version" ] as String,
				"java" to project.extra[ "java_version" ] as String,
				"minecraft" to project.extra[ "minecraft_version" ] as String,
				"fabricloader" to project.extra[ "loader_version" ] as String,
				"fabric_api" to project.extra[ "fabric_version" ] as String,
				"fabric_language_kotlin" to project.extra[ "fabric_language_kotlin_version" ] as String
			) )
		}

		// Mixins
		filesMatching( "*.mixins.json" ) {
			expand( mutableMapOf(
				"java" to project.extra[ "java_version" ] as String
			) )
		}

	}

	java {
		toolchain {
			languageVersion.set( JavaLanguageVersion.of( javaVersion.toString() ) )
		}

		sourceCompatibility = javaVersion
		targetCompatibility = javaVersion

		withSourcesJar()
	}

	test {
		useJUnitPlatform()
	}
}
