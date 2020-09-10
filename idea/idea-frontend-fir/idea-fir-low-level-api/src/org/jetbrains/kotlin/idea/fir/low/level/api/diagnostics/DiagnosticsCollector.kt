/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api.diagnostics

import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.fir.low.level.api.file.builder.ModuleFileCache
import org.jetbrains.kotlin.idea.fir.low.level.api.file.structure.FileStructureCache
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal class DiagnosticsCollector(
    private val fileStructureCache: FileStructureCache,
    private val cache: ModuleFileCache,
) {
    fun getDiagnosticsFor(element: KtElement): List<Diagnostic> =
        fileStructureCache
            .getFileStructure(element.containingKtFile, cache)
            .getStructureElementFor(element)
            .diagnostics.diagnosticsFor(element)

    fun collectDiagnosticsForFile(ktFile: KtFile): Collection<Diagnostic> {
        TODO("Not supported")
    }
}

//private class DiagnosticsForFile(
//    private val ktFile: KtFile,
//    private val firFile: FirFile,
//    private val elementBuilder: FirElementBuilder,
//    private val fileStructureCache: FileStructureCache,
//    private val cache: ModuleFileCache,
//) {
//    private val declarationToDiagnostics = ConcurrentHashMap<KtAnnotated, Map<KtElement, List<Diagnostic>>>()
//
//    fun collectDiagnosticsForFile(): Collection<Diagnostic> =
//        FirIdeFileDiagnosticsCollector.collectForFile(firFile)
//
//    fun getDiagnosticsFor(element: KtElement): List<Diagnostic> {
//        TODO("Not supported yet")
////        val containerDeclaration = element as? KtDeclaration
////            ?: element.containingDeclarationForPseudocode
////            ?: ktFile
////        return declarationToDiagnostics.computeIfAbsent(containerDeclaration) {
////            when (val fir = elementBuilder.getOrBuildFirFor(containerDeclaration, cache, psiToFirCache, FirResolvePhase.BODY_RESOLVE)) {
////                is FirDeclaration -> {
////                    FirIdeDiagnosticsCollector(fir, fir.session).let { collector ->
////                        collector.collectDiagnostics(firFile)
////                        collector.elementToDiagnostic
////                    }
////                    emptyMap()
////                }
////                is FirDiagnosticHolder -> {
////                    emptyMap() //TODO take diagnostic from FirDiagnosticHolder
////                }
////                else -> error("KtDeclaration should be mapped to FirDeclaration")
////            }
////
////        }.getOrDefault(element, emptyList())
//    }
//}
