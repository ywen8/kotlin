/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

interface KotlinFacetSettingsProvider {
    fun getSettings(module: Module): KotlinFacetSettings?
    fun getInitializedSettings(module: Module): KotlinFacetSettings

    companion object {
        fun getInstance(project: Project) = ServiceManager.getService(project, KotlinFacetSettingsProvider::class.java)!!
    }
}
