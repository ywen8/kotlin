/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.run

import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurations
import com.intellij.execution.actions.ConfigurationFromContext
import org.jetbrains.kotlin.idea.run.AbstractKotlinJUnitRunConfigurationProducer
import org.jetbrains.kotlin.idea.run.KotlinJUnitRunConfigurationProducer

class KotlinAndroidJUnitRunConfigurationProducer :
    AbstractKotlinJUnitRunConfigurationProducer<AndroidJUnitConfiguration>(AndroidJUnitConfigurationType.getInstance()) {
    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        if (other.isProducedBy(KotlinJUnitRunConfigurationProducer::class.java)) return true

        return super.shouldReplace(self, other) && AndroidJUnitConfigurations.shouldUseAndroidJUnitConfigurations(self, other)
    }

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        if (other.isProducedBy(KotlinJUnitRunConfigurationProducer::class.java)) return true

        return super.isPreferredConfiguration(self, other) && AndroidJUnitConfigurations.shouldUseAndroidJUnitConfigurations(self, other)
    }
}