/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api.diagnostics

import org.jetbrains.kotlin.cfg.pseudocode.containingDeclarationForPseudocode
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.diagnostics.FirDiagnosticHolder
import org.jetbrains.kotlin.idea.fir.low.level.api.element.builder.FirElementBuilder
import org.jetbrains.kotlin.idea.fir.low.level.api.element.builder.PsiToFirCache
import org.jetbrains.kotlin.idea.fir.low.level.api.file.builder.FirFileBuilder
import org.jetbrains.kotlin.idea.fir.low.level.api.file.builder.ModuleFileCache
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.ConcurrentHashMap

internal class DiagnosticsCollector(
    private val firFileBuilder: FirFileBuilder,
    private val elementBuilder: FirElementBuilder,
    private val psiToFirCache: PsiToFirCache,
    private val cache: ModuleFileCache,
) {
    private val diagnosticsForFile = ConcurrentHashMap<KtFile, DiagnosticsForFile>()

    fun getDiagnosticsFor(element: KtElement): List<Diagnostic> {
        val ktFile = element.containingKtFile
        val diagnostics = getDiagnosticsForKtFile(ktFile)
        return diagnostics.getDiagnosticsFor(element)
    }

    fun collectDiagnosticsForFile(ktFile: KtFile): Collection<Diagnostic> {
        val diagnostics = getDiagnosticsForKtFile(ktFile)
        return diagnostics.collectDiagnosticsForFile()
    }

    private fun getDiagnosticsForKtFile(ktFile: KtFile) = diagnosticsForFile.computeIfAbsent(ktFile) {
        val firFile = firFileBuilder.buildRawFirFileWithCaching(ktFile, cache)
        DiagnosticsForFile(ktFile, firFile, elementBuilder, psiToFirCache, cache)
    }
}

private class DiagnosticsForFile(
    private val ktFile: KtFile,
    private val firFile: FirFile,
    private val elementBuilder: FirElementBuilder,
    private val psiToFirCache: PsiToFirCache,
    private val cache: ModuleFileCache,
) {
    private val declarationToDiagnostics = ConcurrentHashMap<KtAnnotated, Map<KtElement, List<Diagnostic>>>()

    fun collectDiagnosticsForFile(): Collection<Diagnostic> =
        FirIdeFileDiagnosticsCollector.collectForFile(firFile)

    fun getDiagnosticsFor(element: KtElement): List<Diagnostic> {
        TODO("Not supported yet")
//        val containerDeclaration = element as? KtDeclaration
//            ?: element.containingDeclarationForPseudocode
//            ?: ktFile
//        return declarationToDiagnostics.computeIfAbsent(containerDeclaration) {
//            when (val fir = elementBuilder.getOrBuildFirFor(containerDeclaration, cache, psiToFirCache, FirResolvePhase.BODY_RESOLVE)) {
//                is FirDeclaration -> {
//                    FirIdeDiagnosticsCollector(fir, fir.session).let { collector ->
//                        collector.collectDiagnostics(firFile)
//                        collector.elementToDiagnostic
//                    }
//                    emptyMap()
//                }
//                is FirDiagnosticHolder -> {
//                    emptyMap() //TODO take diagnostic from FirDiagnosticHolder
//                }
//                else -> error("KtDeclaration should be mapped to FirDeclaration")
//            }
//
//        }.getOrDefault(element, emptyList())
    }
}
