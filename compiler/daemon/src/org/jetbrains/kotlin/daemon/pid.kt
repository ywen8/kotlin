/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon

import java.lang.management.ManagementFactory


fun getProcessId(): String? {
    // Note: may fail in some JVM implementations

    // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
    val jvmName = ManagementFactory.getRuntimeMXBean().name
    val index = jvmName.indexOf('@')

    if (index < 1) {
        // part before '@' empty (index = 0) / '@' not found (index = -1)
        return null
    }

    try {
        return java.lang.Long.toString(java.lang.Long.parseLong(jvmName.substring(0, index)))
    } catch (e: NumberFormatException) {
        // ignore
    }

    return null
}