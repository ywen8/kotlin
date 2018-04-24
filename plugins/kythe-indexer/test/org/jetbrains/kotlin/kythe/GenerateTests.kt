/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kythe

import org.jetbrains.kotlin.generators.tests.generator.testGroup

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")

    testGroup("plugins/kythe-indexer/test", "plugins/kythe-indexer/testData") {
        testClass<AbstractKytheIndexTest> {
            model("indexer")
        }

        testClass<AbstractSignaturesGeneratorTest> {
            model("signatures")
        }
    }
}