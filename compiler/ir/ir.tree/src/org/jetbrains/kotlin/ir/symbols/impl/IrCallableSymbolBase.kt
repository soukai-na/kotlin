package org.jetbrains.kotlin.ir.symbols.impl

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.WrappedDeclarationDescriptor
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.nameForIrSerialization
import org.jetbrains.kotlin.ir.util.render

abstract class IrCallableSymbolBase<out D : DeclarationDescriptor> @OptIn(ObsoleteDescriptorBasedAPI::class) constructor(
    @ObsoleteDescriptorBasedAPI
    override val descriptor: D,
    override val signature: IdSignature
) : IrSymbol {
    override fun toString(): String {
        if (isBound) return owner.render()
        return "Unbound callable symbol for $signature"
    }
}

abstract class IrBindableCallableSymbolBase<out D : DeclarationDescriptor, B : IrSymbolOwner>(descriptor: D, sig: IdSignature) :
    IrBindableSymbol<D, B>, IrCallableSymbolBase<D>(descriptor, sig) {

    init {
        assert(isOriginalDescriptor(descriptor)) {
            "Substituted descriptor $descriptor for ${descriptor.original}"
        }
        //assert(sig.isPublic)
    }

    private fun isOriginalDescriptor(descriptor: DeclarationDescriptor): Boolean =
        descriptor is WrappedDeclarationDescriptor<*> ||
                // TODO fix declaring/referencing value parameters: compute proper original descriptor
                descriptor is ValueParameterDescriptor && isOriginalDescriptor(descriptor.containingDeclaration) ||
                descriptor == descriptor.original

    private var _owner: B? = null
    override val owner: B
        get() = _owner ?: throw IllegalStateException("Symbol for $signature is unbound")

    override fun bind(owner: B) {
        if (_owner == null) {
            _owner = owner
            if ((owner as IrDeclarationWithName).name.toString() == "finalElement") {
                println("binding finalElement")
                println("The sym is ${this.javaClass.simpleName}@${this.hashCode()}")
                Throwable().printStackTrace()
            }
        } else {
            throw IllegalStateException("${javaClass.simpleName}@${hashCode()} for $signature is already bound: ${owner.render()}")
        }
    }

    // These are private callables for now.
    override val isPublicApi: Boolean = false

    override val isBound: Boolean
        get() = _owner != null
}
/*
class IrClassPublicSymbolImpl(descriptor: ClassDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<ClassDescriptor, IrClass>(descriptor, sig),
    IrClassSymbol

class IrEnumEntryPublicSymbolImpl(descriptor: ClassDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<ClassDescriptor, IrEnumEntry>(descriptor, sig),
    IrEnumEntrySymbol
*/
class IrSimpleFunctionCallableSymbolImpl(descriptor: FunctionDescriptor, sig: IdSignature) :
    IrBindableCallableSymbolBase<FunctionDescriptor, IrSimpleFunction>(descriptor, sig),
    IrSimpleFunctionSymbol

//class IrConstructorPublicSymbolImpl(descriptor: ClassConstructorDescriptor, sig: IdSignature) :
//    IrBindablePublicSymbolBase<ClassConstructorDescriptor, IrConstructor>(descriptor, sig),
//    IrConstructorSymbol

class IrPropertyCallableSymbolImpl(descriptor: PropertyDescriptor, sig: IdSignature) :
    IrBindableCallableSymbolBase<PropertyDescriptor, IrProperty>(descriptor, sig),
    IrPropertySymbol

//class IrTypeAliasPublicSymbolImpl(descriptor: TypeAliasDescriptor, sig: IdSignature) :
//    IrBindablePublicSymbolBase<TypeAliasDescriptor, IrTypeAlias>(descriptor, sig),
//    IrTypeAliasSymbol
