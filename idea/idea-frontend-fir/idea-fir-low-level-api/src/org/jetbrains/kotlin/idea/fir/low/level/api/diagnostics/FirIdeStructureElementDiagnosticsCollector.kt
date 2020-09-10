/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api.diagnostics

import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.idea.fir.low.level.api.file.structure.FileStructureElementDiagnostics
import org.jetbrains.kotlin.idea.fir.low.level.api.util.addValueFor
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtElement

internal class FirIdeStructureElementDiagnosticsCollector private constructor(
    session: FirSession,
    private val declarations: Set<KtAnnotated>,
    private val substitution: Pair<FirDeclaration, FirDeclaration>?,
) : AbstractFirIdeDiagnosticsCollector(
    session,
) {
    private val result = mutableMapOf<KtElement, MutableList<Diagnostic>>()

    override fun onDiagnostic(diagnostic: Diagnostic) {
        (diagnostic.psiElement as? KtElement)?.let { ktElement ->
            result.addValueFor(ktElement, diagnostic)
        }
    }

    override fun needCollectingDiagnosticsForDeclaration(declaration: FirDeclaration): Boolean {
        val declarationPsi = declaration.psi ?: return false
        return declarationPsi in declarations
    }

    override fun getDeclarationToAnalyse(declaration: FirDeclaration): FirDeclaration =
        if (declaration === substitution?.first) substitution.second else declaration

    companion object {
        fun collectForStructureElement(
            declarations: Set<KtAnnotated>,
            firFile: FirFile,
            substitution: Pair<FirDeclaration, FirDeclaration>? = null
        ): FileStructureElementDiagnostics =
            FirIdeStructureElementDiagnosticsCollector(firFile.session, declarations, substitution).let { collector ->
                collector.collectDiagnostics(firFile)
                FileStructureElementDiagnostics(collector.result)
            }
    }
}
