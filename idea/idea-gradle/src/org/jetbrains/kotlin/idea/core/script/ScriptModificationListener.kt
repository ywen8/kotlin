/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.project.Project


fun initializeScriptModificationListener(project: Project) {

}

// The bug in platform (GradleAutoImportAware.getAffectedExternalProjectFiles) was fixed in 183,
// so the changes in .gradle.kts files are tracked correctly and notification 'Gradle project needs to be imported' is shown