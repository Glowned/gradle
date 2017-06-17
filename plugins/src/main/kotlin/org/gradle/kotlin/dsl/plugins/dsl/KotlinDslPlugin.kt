/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.internal.classpath.ModuleRegistry

import org.gradle.kotlin.dsl.gradleKotlinDsl
import org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import javax.inject.Inject


/**
 * The `kotlin-dsl` plugin.
 *
 * Applies the `embedded-kotlin` plugin,
 * adds the `gradleKotlinDsl()` dependency
 * and configures the Kotlin DSL compiler plugins.
 *
 * @see org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin
 */
open class KotlinDslPlugin @Inject protected constructor(
    private val moduleRegistry: ModuleRegistry) : Plugin<Project> {

    override fun apply(project: Project) {
        project.run {

            applyEmbeddedKotlinPlugin()
            addGradleKotlinDslDependency()
            configureCompilerPlugins()
        }
    }

    private
    fun Project.applyEmbeddedKotlinPlugin() {
        plugins.apply(EmbeddedKotlinPlugin::class.java)
    }

    private
    fun Project.addGradleKotlinDslDependency() {
        dependencies.add("compileOnly", gradleKotlinDsl())
    }

    private
    fun Project.configureCompilerPlugins() {
        val compilerPluginModule = moduleRegistry.getExternalModule("gradle-kotlin-dsl-compiler-plugin")
        val compilerPlugin = compilerPluginModule.classpath.asFiles.first()
        require(compilerPlugin.exists()) { "Gradle Kotlin DSL Compiler plugin could not be found! " + compilerPlugin }
        tasks.withType(KotlinCompile::class.java) {
            it.kotlinOptions.freeCompilerArgs += listOf("-Xplugin", compilerPlugin.path)
        }
    }
}
