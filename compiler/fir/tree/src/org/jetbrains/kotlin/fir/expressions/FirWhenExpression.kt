/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions

interface FirWhenExpression : FirExpression {
    val subject: FirExpression?

    // when (val subjectVariable = subject()) { ... }
    val subjectVariable: FirVariable?

    // else branch isn't considered as a regular branch
    val branches: List<FirWhenBranch>

    val elseResult: FirExpression
}