/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinFirCodeBlockModificationListener(project: Project) : KotlinCodeBlockModificationListener(project)  {
    override fun supportInBlockModificationsIn(declaration: KtDeclaration): Boolean =
        declaration is KtNamedFunction
}