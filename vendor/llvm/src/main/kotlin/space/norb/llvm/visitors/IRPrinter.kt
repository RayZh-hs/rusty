package space.norb.llvm.visitors

import space.norb.llvm.structure.Module
import space.norb.llvm.structure.Function
import space.norb.llvm.structure.BasicBlock
import space.norb.llvm.structure.Argument
import space.norb.llvm.values.globals.GlobalVariable
import space.norb.llvm.core.Constant
import space.norb.llvm.core.Type
import space.norb.llvm.core.Value
import space.norb.llvm.core.MetadataCapable
import space.norb.llvm.instructions.terminators.ReturnInst
import space.norb.llvm.instructions.terminators.BranchInst
import space.norb.llvm.instructions.terminators.SwitchInst
import space.norb.llvm.instructions.terminators.UnreachableInst
import space.norb.llvm.instructions.binary.AddInst
import space.norb.llvm.instructions.binary.SubInst
import space.norb.llvm.instructions.binary.MulInst
import space.norb.llvm.instructions.binary.SDivInst
import space.norb.llvm.instructions.binary.UDivInst
import space.norb.llvm.instructions.binary.URemInst
import space.norb.llvm.instructions.binary.FAddInst
import space.norb.llvm.instructions.binary.FSubInst
import space.norb.llvm.instructions.binary.FMulInst
import space.norb.llvm.instructions.binary.FDivInst
import space.norb.llvm.instructions.binary.FRemInst
import space.norb.llvm.instructions.binary.SRemInst
import space.norb.llvm.instructions.binary.AndInst
import space.norb.llvm.instructions.binary.OrInst
import space.norb.llvm.instructions.binary.XorInst
import space.norb.llvm.instructions.binary.LShrInst
import space.norb.llvm.instructions.binary.AShrInst
import space.norb.llvm.instructions.binary.ShlInst
import space.norb.llvm.instructions.memory.AllocaInst
import space.norb.llvm.instructions.memory.LoadInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.memory.GetElementPtrInst
import space.norb.llvm.instructions.casts.TruncInst
import space.norb.llvm.instructions.casts.ZExtInst
import space.norb.llvm.instructions.casts.SExtInst
import space.norb.llvm.instructions.casts.BitcastInst
import space.norb.llvm.instructions.casts.PtrToIntInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.instructions.other.ICmpInst
import space.norb.llvm.instructions.other.FCmpInst
import space.norb.llvm.instructions.other.PhiNode
import space.norb.llvm.instructions.other.CommentAttachment
import space.norb.llvm.values.Metadata
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.base.BinaryInst
import space.norb.llvm.instructions.base.MemoryInst
import space.norb.llvm.instructions.base.CastInst
import space.norb.llvm.instructions.base.OtherInst
import space.norb.llvm.enums.LinkageType

/**
 * Visitor for printing LLVM IR to string format.
 *
 * This IR printer generates LLVM IR using un-typed pointers, complying with the
 * latest LLVM IR standard. All pointers are represented as "ptr" regardless of
 * the element type they point to.
 *
 * Examples of IR output:
 * ```
 * %ptr = alloca i32
 * %val = load i32, ptr %ptr
 * %gep = getelementptr [10 x i32], ptr %array, i64 0, i64 5
 * ```
 *
 * Key characteristics:
 * - All pointer types are represented as "ptr" instead of "elementType*"
 * - Type information is conveyed through other mechanisms (e.g., explicit types in instructions)
 * - Pointer operations require explicit type information where needed
 */
class IRPrinter : IRVisitor<Unit> {
    private val output = StringBuilder()
    private var indentLevel = 0

    private fun requireName(name: String?, kind: String): String =
        requireNotNull(name) { "$kind must have a name to be printed" }

    private fun formatLocalName(name: String?): String = "%${name ?: "unnamed"}"
    
    fun print(module: Module): String {
        visitModule(module)
        return output.toString()
    }
    
    private fun indent() = "  ".repeat(indentLevel)

    private fun formatMetadata(capable: MetadataCapable, includeComma: Boolean = false): String {
        if (!capable.hasMetadata()) return ""
        val prefix = if (includeComma) ", " else " "
        return prefix + capable.getAllMetadata().entries.joinToString(", ") { (kind, md) ->
            "!$kind ${md.toIRString()}"
        }
    }

    private fun appendInstructionLine(inst: Instruction, line: String) {
        val metadataStr = formatMetadata(inst, includeComma = true)
        val fullLine = "$line$metadataStr"
        
        val inlineComment = inst.inlineComment
        if (inlineComment.isNullOrEmpty()) {
            output.appendLine(fullLine)
            return
        }

        val commentLines = inlineComment.lines()
        val firstLine = commentLines.firstOrNull() ?: ""
        val suffix = if (firstLine.isEmpty()) " ;" else " ; $firstLine"
        output.appendLine("$fullLine$suffix")

        if (commentLines.size > 1) {
            commentLines.drop(1).forEach { extra ->
                val commentSuffix = if (extra.isEmpty()) ";" else "; $extra"
                output.appendLine("${indent()}$commentSuffix")
            }
        }
    }
    
    override fun visitModule(module: Module) {
        output.appendLine("; Module: ${module.name}")

        module.targetTriple?.let {
            output.appendLine("target triple = \"$it\"")
        }
        module.dataLayout?.let {
            output.appendLine("target datalayout = \"$it\"")
        }
        if (module.targetTriple != null || module.dataLayout != null) {
            output.appendLine()
        }
        
        // Emit struct definitions first
        emitStructDefinitions(module)
        
        // Print global variables
        module.globalVariables.forEach { visitGlobalVariable(it) }
        
        // Then print functions, ensuring clean separation from previous content
        module.functions.forEachIndexed { index, function ->
            val needsSeparator = index > 0 ||
                module.globalVariables.isNotEmpty() ||
                module.getAllNamedStructTypes().isNotEmpty()
            
            if (needsSeparator) {
                output.appendLine()
            }
            
            visitFunction(function)
        }
        
        // Print named metadata at the end
        if (module.namedMetadata.isNotEmpty()) {
            output.appendLine()
            module.namedMetadata.forEach { (name, metadata) ->
                output.appendLine("!$name = ${metadata.toIRString()}")
            }
        }
    }
    /**
     * Emits struct definitions for all named struct types in the module.
     * Anonymous structs are not emitted here as they appear inline.
     */
    private fun emitStructDefinitions(module: Module) {
        val namedStructs = module.getAllNamedStructTypes()
        if (namedStructs.isNotEmpty()) {
            output.appendLine()
            namedStructs.forEach { structType ->
                output.appendLine(structType.toDefinitionString())
            }
        }
    }
    
    override fun visitFunction(function: Function) {
        // Check for invalid external declarations with bodies
        if (function.isDeclaration && function.basicBlocks.isNotEmpty()) {
            throw IllegalArgumentException("External function '${function.name}' cannot have a body. External functions must be declarations only.")
        }
        
        val isExternalDeclaration = function.isDeclaration ||
            (function.linkage == LinkageType.EXTERNAL && function.basicBlocks.isEmpty())
        
        // Build function signature
        val returnTypeStr = if (function.returnType.isPointerType()) {
            "ptr" // Use un-typed pointer syntax
        } else {
            function.returnType.toString()
        }
        
        val paramsStr = if (function.type.isVarArg && function.parameters.isNotEmpty()) {
            // Handle vararg functions: add parameters first, then ...
            val namedParams = function.parameters.joinToString(", ") { param ->
                val paramTypeStr = if (param.type.isPointerType()) {
                    "ptr" // Use un-typed pointer syntax
                } else {
                    param.type.toString()
                }
                "$paramTypeStr ${formatLocalName(param.name)}" // Include parameter names with % prefix
            }
            "$namedParams, ..."
        } else if (function.type.isVarArg) {
            // Vararg with no named parameters
            "..."
        } else {
            // Regular function
            function.parameters.joinToString(", ") { param ->
                val paramTypeStr = if (param.type.isPointerType()) {
                    "ptr" // Use un-typed pointer syntax
                } else {
                    param.type.toString()
                }
                "$paramTypeStr ${formatLocalName(param.name)}" // Include parameter names with % prefix
            }
        }
        
        // Handle linkage
        val linkageStr = when (function.linkage.name) {
            "EXTERNAL" -> if (isExternalDeclaration) {
                "declare "
            } else {
                "define "
            }
            "INTERNAL" -> "internal define "
            "PRIVATE" -> "private define "
            "LINK_ONCE" -> "linkonce define "
            "WEAK" -> "weak define "
            "COMMON" -> "common define "
            "APPENDING" -> "appending define "
            "EXTERN_WEAK" -> "extern_weak "
            "AVAILABLE_EXTERNALLY" -> "available_externally define "
            "DLL_IMPORT" -> "dllimport define "
            "DLL_EXPORT" -> "dllexport define "
            "EXTERNAL_WEAK" -> "extern_weak "
            "GHOST" -> "ghost define "
            "LINKER_PRIVATE" -> "linker_private define "
            "LINKER_PRIVATE_WEAK" -> "linker_private_weak define "
            else -> "define " // Default to define
        }
        
        // For external declarations (no body), don't include parameter names and don't include the body braces
        if (isExternalDeclaration) {
            // For external declarations, parameter names should not be included
            val declareParamsStr = if (function.type.isVarArg && function.parameters.isNotEmpty()) {
                // Handle vararg functions: add parameter types first, then ...
                val paramTypes = function.parameters.joinToString(", ") { param ->
                    val paramTypeStr = if (param.type.isPointerType()) {
                        "ptr" // Use un-typed pointer syntax
                    } else {
                        param.type.toString()
                    }
                    paramTypeStr // No parameter names for declarations
                }
                "$paramTypes, ..."
            } else if (function.type.isVarArg) {
                // Vararg with no named parameters
                "..."
            } else {
                // Regular function
                function.parameters.joinToString(", ") { param ->
                    val paramTypeStr = if (param.type.isPointerType()) {
                        "ptr" // Use un-typed pointer syntax
                    } else {
                        param.type.toString()
                    }
                    paramTypeStr // No parameter names for declarations
                }
            }
            val attributeStr = formatFunctionAttributes(function)
            val metadataStr = formatMetadata(function)
            output.appendLine("${linkageStr}$returnTypeStr @${requireName(function.name, "Function")}($declareParamsStr)${attributeStr}${metadataStr}")
        } else {
            val attributeStr = formatFunctionAttributes(function)
            val metadataStr = formatMetadata(function)
            output.appendLine("${linkageStr}$returnTypeStr @${requireName(function.name, "Function")}($paramsStr)${attributeStr}${metadataStr} {")
            val previousIndent = indentLevel
            indentLevel = 0
            function.basicBlocks.forEachIndexed { index, block ->
                if (index > 0) {
                    output.appendLine()
                }
                visitBasicBlock(block)
            }
            indentLevel = previousIndent
            output.appendLine("}")
        }
    }
    
    override fun visitBasicBlock(block: BasicBlock) {
        output.appendLine("${indent()}${requireName(block.name, "Basic block")}:")
        indentLevel++
        block.instructions.forEach { it.accept(this) }
        indentLevel--
    }
    
    override fun visitArgument(argument: Argument) {
        val typeStr = if (argument.type.isPointerType()) {
            "ptr" // Use un-typed pointer syntax
        } else {
            argument.type.toString()
        }
        output.append("$typeStr ${formatLocalName(argument.name)}")
    }
    
    override fun visitGlobalVariable(globalVariable: GlobalVariable) {
        val linkageStr = when (globalVariable.linkage.name) {
            "EXTERNAL" -> {
                // For EXTERNAL linkage, don't use "external" prefix - treat as default linkage
                ""
            }
            "INTERNAL" -> "internal "
            else -> "${globalVariable.linkage.name.lowercase()} "
        }
        
        val constantStr = if (globalVariable.isConstant()) {
            "constant "
        } else {
            ""
        }
        
        // Use element type if available, otherwise use pointer type
        val typeStr = globalVariable.elementType?.toString() ?: globalVariable.type.toString()
        
        val initializerStr = if (globalVariable.hasInitializer()) {
            " ${formatValueName(globalVariable.initializer!!)}"
        } else {
            // For EXTERNAL linkage globals without explicit initializers, provide zeroinitializer
            // but don't mark them as "external" declarations
            " zeroinitializer"
        }
        
        // Include "global" keyword for all non-constant globals, including external ones
        val globalKeyword = if (!globalVariable.isConstant()) {
            "global "
        } else {
            ""
        }
        
        val metadataStr = formatMetadata(globalVariable, includeComma = true)
        output.appendLine("@${requireName(globalVariable.name, "Global variable")} = ${linkageStr}${constantStr}${globalKeyword}${typeStr}${initializerStr}${metadataStr}")
    }
    
    override fun visitConstant(constant: Constant) {
        output.append(constant.toString())
    }
    
    override fun visitMetadata(metadata: Metadata) {
        output.append(metadata.toIRString())
    }
    
    override fun visitReturnInst(inst: ReturnInst) {
        val operands = inst.getOperandsList()
        val value = operands.firstOrNull()
        if (value != null) {
            appendInstructionLine(inst, "${indent()}ret ${value.type} ${formatValueName(value)}")
        } else {
            appendInstructionLine(inst, "${indent()}ret void")
        }
    }
    
    override fun visitBranchInst(inst: BranchInst) {
        val operands = inst.getOperandsList()
        if (operands.size == 1) {
            // Unconditional branch
            appendInstructionLine(inst, "${indent()}br label ${formatBlockName(operands.first() as BasicBlock)}")
        } else {
            // Conditional branch
            val condition = operands[0]
            val trueBlock = operands[1] as BasicBlock
            val falseBlock = operands[2] as BasicBlock
            appendInstructionLine(inst, "${indent()}br ${condition.type} ${formatValueName(condition)}, label ${formatBlockName(trueBlock)}, label ${formatBlockName(falseBlock)}")
        }
    }
    
    override fun visitSwitchInst(inst: SwitchInst) {
        val operands = inst.getOperandsList()
        appendInstructionLine(inst, "${indent()}switch ${operands[0].type} ${formatValueName(operands[0])}, label ${formatBlockName(operands[1] as BasicBlock)} [")
        indentLevel++
        val cases = inst.getCases()
        cases.forEach { (value, destination) ->
            output.appendLine("${indent()}${value.type} ${formatValueName(value)}, label ${formatBlockName(destination as BasicBlock)}")
        }
        indentLevel--
        output.appendLine("${indent()}]")
    }
    
    override fun visitUnreachableInst(inst: UnreachableInst) {
        appendInstructionLine(inst, "${indent()}unreachable")
    }
    
    override fun visitAddInst(inst: AddInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = add ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitSubInst(inst: SubInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = sub ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitMulInst(inst: MulInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = mul ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitSDivInst(inst: SDivInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = sdiv ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitUDivInst(inst: UDivInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = udiv ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitURemInst(inst: URemInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = urem ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitSRemInst(inst: SRemInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = srem ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitFAddInst(inst: FAddInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = fadd ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitFSubInst(inst: FSubInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = fsub ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitFMulInst(inst: FMulInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = fmul ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitFDivInst(inst: FDivInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = fdiv ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitFRemInst(inst: FRemInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = frem ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitAndInst(inst: AndInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = and ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitOrInst(inst: OrInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = or ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitXorInst(inst: XorInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = xor ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitLShrInst(inst: LShrInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = lshr ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitAShrInst(inst: AShrInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = ashr ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }

    override fun visitShlInst(inst: ShlInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = shl ${inst.lhs.type} ${formatValueName(inst.lhs)}, ${formatValueName(inst.rhs)}")
    }
    
    override fun visitAllocaInst(inst: AllocaInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = alloca ${inst.allocatedType}")
        // Note: alloca result type is implicit and handled by the instruction's type property
        // The instruction's type will be printed as "ptr" when accessed through other methods
    }
    
    override fun visitLoadInst(inst: LoadInst) {
        val pointer = inst.pointer
        val pointerTypeStr = if (pointer.type.isPointerType()) {
            "ptr" // Use un-typed pointer syntax
        } else {
            pointer.type.toString()
        }
        appendInstructionLine(inst, "${indent()}%${inst.name} = load ${inst.loadedType}, $pointerTypeStr ${formatValueName(pointer)}")
    }
    
    override fun visitStoreInst(inst: StoreInst) {
        val value = inst.value
        val pointer = inst.pointer
        val pointerTypeStr = if (pointer.type.isPointerType()) {
            "ptr" // Use un-typed pointer syntax
        } else {
            pointer.type.toString()
        }
        appendInstructionLine(inst, "${indent()}store ${value.type} ${formatValueName(value)}, $pointerTypeStr ${formatValueName(pointer)}")
    }
    
    override fun visitGetElementPtrInst(inst: GetElementPtrInst) {
        val pointer = inst.pointer
        val indices = inst.indices
        val pointerTypeStr = if (pointer.type.isPointerType()) {
            "ptr" // Use un-typed pointer syntax
        } else {
            pointer.type.toString()
        }
        val indicesStr = indices.joinToString(", ") { "${it.type} ${formatValueName(it)}" }
        appendInstructionLine(inst, "${indent()}%${inst.name} = getelementptr ${inst.elementType}, $pointerTypeStr ${formatValueName(pointer)}, $indicesStr")
    }
    
    override fun visitTruncInst(inst: TruncInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = trunc ${inst.value.type} ${formatValueName(inst.value)} to ${inst.type}")
    }
    
    override fun visitZExtInst(inst: ZExtInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = zext ${inst.value.type} ${formatValueName(inst.value)} to ${inst.type}")
    }
    
    override fun visitSExtInst(inst: SExtInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = sext ${inst.value.type} ${formatValueName(inst.value)} to ${inst.type}")
    }
    
    override fun visitBitcastInst(inst: BitcastInst) {
        val targetTypeStr = if (inst.type.isPointerType()) {
            "ptr" // Use un-typed pointer syntax
        } else {
            inst.type.toString()
        }
        
        val sourceTypeStr = if (inst.value is space.norb.llvm.structure.Function) {
            // For function values, print the function type with * to indicate function pointer
            "${inst.value.type}*"
        } else {
            inst.value.type.toString()
        }
        
        val sourceValueName = if (inst.value is space.norb.llvm.structure.Function) {
            "@${requireName(inst.value.name, "Global value")}" 
        } else {
            formatValueName(inst.value)
        }
        
        appendInstructionLine(inst, "${indent()}%${inst.name} = bitcast $sourceTypeStr $sourceValueName to $targetTypeStr")
    }

    override fun visitPtrToIntInst(inst: PtrToIntInst) {
        appendInstructionLine(inst, "${indent()}%${inst.name} = ptrtoint ${inst.value.type} ${formatValueName(inst.value)} to ${inst.type}")
    }
    
    override fun visitCallInst(inst: CallInst) {
        val callee = inst.callee
        val arguments = inst.arguments
        
        // Format the return type
        val returnTypeStr = if (inst.type.isPointerType()) {
            "ptr" // Use un-typed pointer syntax
        } else {
            inst.type.toString()
        }
        
        // Format arguments with their types
        val argsStr = arguments.joinToString(", ") { arg ->
            val argTypeStr = if (arg.type.isPointerType()) {
                "ptr" // Use un-typed pointer syntax
            } else {
                arg.type.toString()
            }
            "$argTypeStr ${formatValueName(arg)}"
        }
        
        // Format the callee name (will use @ for direct functions, % for indirect)
        val calleeName = formatValueName(callee)

        val resultPrefix = if (inst.producesValue()) "${formatLocalName(inst.name)} = " else ""
        appendInstructionLine(inst, "${indent()}${resultPrefix}call $returnTypeStr $calleeName($argsStr)")
    }
    
    override fun visitICmpInst(inst: ICmpInst) {
        val operands = inst.getOperandsList()
        appendInstructionLine(inst, "${indent()}%${inst.name} = icmp ${inst.predicate.toString().lowercase()} ${operands[0].type} ${formatValueName(operands[0])}, ${formatValueName(operands[1])}")
    }

    override fun visitFCmpInst(inst: FCmpInst) {
        val operands = inst.getOperandsList()
        appendInstructionLine(inst, "${indent()}%${inst.name} = fcmp ${inst.predicate.toString().lowercase()} ${operands[0].type} ${formatValueName(operands[0])}, ${formatValueName(operands[1])}")
    }
    
    override fun visitPhiNode(inst: PhiNode) {
        val pairsStr = inst.incomingValues.joinToString(", ") { (value, block) ->
            "[ ${formatValueName(value)}, ${formatBlockName(block as BasicBlock)} ]"
        }
        appendInstructionLine(inst, "${indent()}%${inst.name} = phi ${inst.type} $pairsStr")
    }

    override fun visitCommentAttachment(inst: CommentAttachment) {
        val lines = inst.comment.lines()
        if (lines.isEmpty()) {
            output.appendLine("${indent()};")
            return
        }
        lines.forEach { line ->
            val suffix = if (line.isEmpty()) "" else " ${line}"
            output.appendLine("${indent()};${suffix}")
        }
    }
    
    override fun visitTerminatorInst(inst: TerminatorInst): Unit = when (inst) {
        is ReturnInst -> visitReturnInst(inst)
        is BranchInst -> visitBranchInst(inst)
        is SwitchInst -> visitSwitchInst(inst)
        is UnreachableInst -> visitUnreachableInst(inst)
        else -> throw IllegalArgumentException("Unknown terminator instruction: ${inst::class.simpleName}")
    }
    
    override fun visitBinaryInst(inst: BinaryInst): Unit = when (inst) {
        is AddInst -> visitAddInst(inst)
        is SubInst -> visitSubInst(inst)
        is MulInst -> visitMulInst(inst)
        is SDivInst -> visitSDivInst(inst)
        is UDivInst -> visitUDivInst(inst)
        is URemInst -> visitURemInst(inst)
        is SRemInst -> visitSRemInst(inst)
        is FAddInst -> visitFAddInst(inst)
        is FSubInst -> visitFSubInst(inst)
        is FMulInst -> visitFMulInst(inst)
        is FDivInst -> visitFDivInst(inst)
        is FRemInst -> visitFRemInst(inst)
        is AndInst -> visitAndInst(inst)
        is OrInst -> visitOrInst(inst)
        is XorInst -> visitXorInst(inst)
        is LShrInst -> visitLShrInst(inst)
        is AShrInst -> visitAShrInst(inst)
        is ShlInst -> visitShlInst(inst)
        else -> throw IllegalArgumentException("Unknown binary instruction: ${inst::class.simpleName}")
    }
    
    override fun visitMemoryInst(inst: MemoryInst): Unit = when (inst) {
        is AllocaInst -> visitAllocaInst(inst)
        is LoadInst -> visitLoadInst(inst)
        is StoreInst -> visitStoreInst(inst)
        is GetElementPtrInst -> visitGetElementPtrInst(inst)
        else -> throw IllegalArgumentException("Unknown memory instruction: ${inst::class.simpleName}")
    }
    
    override fun visitCastInst(inst: CastInst): Unit = when (inst) {
        is TruncInst -> visitTruncInst(inst)
        is ZExtInst -> visitZExtInst(inst)
        is SExtInst -> visitSExtInst(inst)
        is BitcastInst -> visitBitcastInst(inst)
        is PtrToIntInst -> visitPtrToIntInst(inst)
        else -> throw IllegalArgumentException("Unknown cast instruction: ${inst::class.simpleName}")
    }
    
    override fun visitOtherInst(inst: OtherInst): Unit = when (inst) {
        is CallInst -> visitCallInst(inst)
        is ICmpInst -> visitICmpInst(inst)
        is FCmpInst -> visitFCmpInst(inst)
        is PhiNode -> visitPhiNode(inst)
        is CommentAttachment -> visitCommentAttachment(inst)
        else -> throw IllegalArgumentException("Unknown other instruction: ${inst::class.simpleName}")
    }
    
    private fun formatValueName(value: Value): String {
        return when {
            value is space.norb.llvm.structure.Function -> {
                // Direct function calls should use @ prefix
                "@${requireName(value.name, "Function")}"
            }
            value is Constant -> {
                when (value) {
                    is space.norb.llvm.values.constants.IntConstant -> {
                        if (value.isUnsigned && value.value < 0) {
                            // Convert negative Long to unsigned representation
                            val unsignedValue = value.value.toULong()
                            unsignedValue.toString()
                        } else {
                            value.value.toString()
                        }
                    }
                    is space.norb.llvm.values.constants.FloatConstant -> {
                        // Format floating point constants in LLVM IR format
                        formatFloatConstant(value)
                    }
                    is space.norb.llvm.values.globals.GlobalVariable -> "@${requireName(value.name, "Global variable")}"
                    else -> {
                        // For other constants, if they have a name use it, otherwise use toString
                        if (!value.name.isNullOrEmpty()) formatLocalName(value.name) else value.toString()
                    }
                }
            }
            !value.name.isNullOrEmpty() -> formatLocalName(value.name)
            else -> "0" // Default for unnamed values
        }
    }
    
    private fun formatFloatConstant(constant: space.norb.llvm.values.constants.FloatConstant): String {
        val value = constant.value
        val type = constant.type
        
        return when {
            value.isNaN() -> "nan"
            value == Double.POSITIVE_INFINITY -> "inf"
            value == Double.NEGATIVE_INFINITY -> "-inf"
            else -> {
                if (type is space.norb.llvm.types.FloatingPointType.FloatType) {
                    // For float type, use scientific notation with 6 decimal places
                    String.format("%.6e", value.toFloat())
                } else {
                    // For double type, use scientific notation with 6 decimal places
                    String.format("%.6e", value)
                }
            }
        }
    }
    
    private fun formatBlockName(block: BasicBlock): String {
        return formatLocalName(block.name)
    }

    private fun formatFunctionAttributes(function: Function): String {
        if (function.attributes.isEmpty()) return ""
        return function.attributes.joinToString(prefix = " ", separator = " ")
    }
}
