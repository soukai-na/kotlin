/*
 * Copyright 2010-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.common.overrides

import org.jetbrains.kotlin.backend.common.ir.ir2stringWhole
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData
import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignatureSerializer
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.descriptors.WrappedPropertyDescriptor
import org.jetbrains.kotlin.ir.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrPropertySymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.extractTypeParameters
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.types.Variance

interface PlatformFakeOverrideClassFilter {
    fun constructFakeOverrides(clazz: IrClass): Boolean = true
}

interface FileLocalLinker {
    fun provideIrSymbolExternally(idSignature: IdSignature, symbolKind: BinarySymbolData.SymbolKind): IrSymbol
}

object DefaultFakeOverrideClassFilter : PlatformFakeOverrideClassFilter

object FakeOverrideControl {
    // If set to true: all fake overrides go to klib serialized IR.
    // If set to false: eligible fake overrides are not serialized.
    val serializeFakeOverrides: Boolean = true

    // If set to true: fake overrides are deserialized from klib serialized IR.
    // If set to false: eligible fake overrides are constructed within IR linker.
    // This is the default in the absence of -Xdeserialize-fake-overrides flag.
    val deserializeFakeOverrides: Boolean = false
}

class FakeOverrideBuilder(
    val symbolTable: SymbolTable,
    val signaturer: IdSignatureSerializer,
    val irBuiltIns: IrBuiltIns,
    val platformSpecificClassFilter: PlatformFakeOverrideClassFilter = DefaultFakeOverrideClassFilter
) : FakeOverrideBuilderStrategy {
    private val haveFakeOverrides = mutableSetOf<IrClass>()
    override val propertyOverriddenSymbols = mutableMapOf<IrOverridableMember, List<IrSymbol>>()
    private val irOverridingUtil = IrOverridingUtil(irBuiltIns, this)

    private var localLinker: FileLocalLinker? = null

    override fun fakeOverrideMember(
        superType: IrType,
        member: IrOverridableMember,
        clazz: IrClass
    ): IrOverridableMember {
        require(superType is IrSimpleType) { "superType is $superType, expected IrSimpleType" }
        val classifier = superType.classifier
        require(classifier is IrClassSymbol) { "superType classifier is not IrClassSymbol: $classifier" }

        val typeParameters = extractTypeParameters(classifier.owner)
        val superArguments = superType.arguments
        assert(typeParameters.size == superArguments.size) {
            "typeParameters = $typeParameters size != typeArguments = $superArguments size "
        }

        val substitutionMap = mutableMapOf<IrTypeParameterSymbol, IrType>()

        for (i in typeParameters.indices) {
            val tp = typeParameters[i]
            val ta = superArguments[i]
            require(ta is IrTypeProjection) { "Unexpected super type argument: $ta @ $i" }
            assert(ta.variance == Variance.INVARIANT) { "Unexpected variance in super type argument: ${ta.variance} @$i" }
            substitutionMap[tp.symbol] = ta.type
        }

        val copier = DeepCopyIrTreeWithSymbolsForFakeOverrides(substitutionMap)
        val deepCopyFakeOverride = copier.copy(member, clazz) as IrOverridableMember
        deepCopyFakeOverride.parent = clazz

        println("CANDIDATE: ${deepCopyFakeOverride.render()}")

        return deepCopyFakeOverride
    }

    fun buildFakeOverrideChainsForClass(clazz: IrClass) {
        if (haveFakeOverrides.contains(clazz)) return
        if (!platformSpecificClassFilter.constructFakeOverrides(clazz)/* || !clazz.symbol.isPublicApi*/) return

        println("FAKE OVERRIDES FOR "+ir2stringWhole(clazz))

        val superTypes = clazz.superTypes

        val superClasses = superTypes.map {
            it.getClass() ?: error("Unexpected super type: $it")
        }

        superClasses.forEach {
            buildFakeOverrideChainsForClass(it)
            haveFakeOverrides.add(it)
        }

        irOverridingUtil.buildFakeOverridesForClass(clazz)
    }

    override fun linkFakeOverride(fakeOverride: IrOverridableMember) {
        when (fakeOverride) {
            is IrFakeOverrideFunction -> linkFunctionFakeOverride(fakeOverride)
            is IrFakeOverrideProperty -> linkPropertyFakeOverride(fakeOverride)
            else -> error("Unexpected fake override: $fakeOverride")
        }
    }

    private fun linkFunctionFakeOverride(declaration: IrFakeOverrideFunction) {
        val signature = if ((declaration.parent as IrClass).symbol.isPublicApi && !((declaration as IrFunction).visibility == Visibilities.PRIVATE)) {
            signaturer.composePublicIdSignature(declaration)
        } else {
            signaturer.composeFileLocalIdSignature(declaration)
        }
        println("LINKING ${declaration.nameForIrSerialization} in ${(declaration.parent as IrClass).name}\nsig = $signature")
        if (signature.toString() == "private kotlinx.cinterop/|null[0]:3:4638265728071529943") {
            symbolTable.allUnbound.forEach {
                try {
                    println("$it ${it.signature}")
                } catch (e: Throwable) {
                    println(it)
                }
            }
        }
        if ((declaration.parent as IrClass).symbol.isPublicApi && !((declaration as IrFunction).visibility == Visibilities.PRIVATE)) {
            symbolTable.declareSimpleFunctionFromLinker(WrappedSimpleFunctionDescriptor(), signature) {
                declaration.acquireSymbol(it)
            }
        } else {
            localLinker!!.provideIrSymbolExternally(signature, BinarySymbolData.SymbolKind.FUNCTION_SYMBOL)
        }
    }

    private fun linkPropertyFakeOverride(declaration: IrFakeOverrideProperty) {
        // To compute a signature for a property with type parameters,
        // we must have its accessor's correspondingProperty pointing to the property's symbol.
        // See IrMangleComputer.mangleTypeParameterReference() for details.
        // But to create and link that symbol we should already have the signature computed.
        // To break this loop we use temp symbol in correspondingProperty.

        val tempSymbol = IrPropertySymbolImpl(WrappedPropertyDescriptor()).also {
            it.bind(declaration as IrProperty)
        }
        declaration.getter?.let {
            it.correspondingPropertySymbol = tempSymbol
        }
        declaration.setter?.let {
            it.correspondingPropertySymbol = tempSymbol
        }

        val signature = if ((declaration.parent as IrClass).symbol.isPublicApi && !((declaration as IrProperty).visibility == Visibilities.PRIVATE)) {
            signaturer.composePublicIdSignature(declaration)
        } else {
            signaturer.composeFileLocalIdSignature(declaration)
        }

        if ((declaration.parent as IrClass).symbol.isPublicApi && !((declaration as IrProperty).visibility == Visibilities.PRIVATE)) {
            symbolTable.declarePropertyFromLinker(WrappedPropertyDescriptor(), signature) {
                declaration.acquireSymbol(it)
            }
        } else {
            localLinker!!.provideIrSymbolExternally(signature, BinarySymbolData.SymbolKind.PROPERTY_SYMBOL)
        }

        declaration.getter?.let {
            it.correspondingPropertySymbol = declaration.symbol
            linkFunctionFakeOverride(it as? IrFakeOverrideFunction ?: error("Unexpected fake override getter: $it"))
        }
        declaration.setter?.let {
            it.correspondingPropertySymbol = declaration.symbol
            linkFunctionFakeOverride(it as? IrFakeOverrideFunction ?: error("Unexpected fake override setter: $it"))
        }
    }

    fun provideFakeOverrides(klass: IrClass, fileLocalLinker: FileLocalLinker) {
        this.localLinker = fileLocalLinker // TODO: make me an extension point.
        buildFakeOverrideChainsForClass(klass)
        propertyOverriddenSymbols.clear()
        irOverridingUtil.clear()
        haveFakeOverrides.add(klass)
    }
}
