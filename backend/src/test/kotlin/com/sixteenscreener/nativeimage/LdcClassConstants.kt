package com.sixteenscreener.nativeimage

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer

object LdcClassConstants {

    fun binaryNamesIn(classFile: ByteArray): Set<String> {
        val input = DataInputStream(ByteArrayInputStream(classFile))
        input.skipBytes(MAGIC_AND_VERSION_BYTES)
        val constantPool = readConstantPool(input)
        input.skipBytes(ACCESS_THIS_SUPER_BYTES)
        input.skipBytes(input.readUnsignedShort() * U2_BYTES)
        readMembers(input, constantPool)
        val codes = readMembers(input, constantPool)
        return codes.asSequence()
            .flatMap(::ldcIndices)
            .mapNotNull { constantPool[it] as? Constant.ClassRef }
            .map { (constantPool[it.nameIndex] as Constant.Utf8).value }
            .filterNot { it.startsWith('[') }
            .map { it.replace('/', '.') }
            .toSet()
    }

    private sealed interface Constant {
        data class Utf8(val value: String) : Constant
        data class ClassRef(val nameIndex: Int) : Constant
        data object Other : Constant
    }

    private fun readConstantPool(input: DataInputStream): List<Constant?> {
        val count = input.readUnsignedShort()
        val pool = MutableList<Constant?>(count) { null }
        var index = 1
        while (index < count) {
            val tag = input.readUnsignedByte()
            pool[index] = readConstant(tag, input)
            index += if (tag == TAG_LONG || tag == TAG_DOUBLE) 2 else 1
        }
        return pool
    }

    private fun readConstant(tag: Int, input: DataInputStream): Constant = when (tag) {
        TAG_UTF8 -> Constant.Utf8(input.readUTF())
        TAG_CLASS -> Constant.ClassRef(input.readUnsignedShort())
        else -> Constant.Other.also { input.skipBytes(constantBodyBytes(tag)) }
    }

    private fun constantBodyBytes(tag: Int): Int = when (tag) {
        TAG_STRING, TAG_METHOD_TYPE, TAG_MODULE, TAG_PACKAGE -> 2
        TAG_METHOD_HANDLE -> 3
        TAG_INTEGER, TAG_FLOAT, TAG_FIELD_REF, TAG_METHOD_REF, TAG_INTERFACE_METHOD_REF, TAG_NAME_AND_TYPE,
        TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> 4
        TAG_LONG, TAG_DOUBLE -> 8
        else -> error("Unknown constant pool tag $tag")
    }

    private fun readMembers(input: DataInputStream, constantPool: List<Constant?>): List<ByteArray> =
        List(input.readUnsignedShort()) {
            input.skipBytes(MEMBER_HEADER_BYTES)
            readAttributes(input)
                .filter { (nameIndex, _) -> (constantPool[nameIndex] as? Constant.Utf8)?.value == CODE_ATTRIBUTE }
                .map { (_, body) -> codeOf(body) }
        }.flatten()

    private fun readAttributes(input: DataInputStream): List<Pair<Int, ByteArray>> =
        List(input.readUnsignedShort()) {
            val nameIndex = input.readUnsignedShort()
            nameIndex to ByteArray(input.readInt()).also(input::readFully)
        }

    private fun codeOf(codeAttribute: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(codeAttribute)
        val length = buffer.getInt(MAX_STACK_AND_LOCALS_BYTES)
        val start = MAX_STACK_AND_LOCALS_BYTES + Int.SIZE_BYTES
        return codeAttribute.copyOfRange(start, start + length)
    }

    private fun ldcIndices(code: ByteArray): Sequence<Int> =
        generateSequence(0.takeIf { code.isNotEmpty() }) { pc ->
            (pc + instructionLength(code, pc)).takeIf { it < code.size }
        }.mapNotNull { pc ->
            when (code.u1(pc)) {
                LDC -> code.u1(pc + 1)
                LDC_W -> code.u2(pc + 1)
                else -> null
            }
        }

    private fun instructionLength(code: ByteArray, pc: Int): Int = when (val opcode = code.u1(pc)) {
        TABLESWITCH -> {
            val operands = pc + 1 + switchPadding(pc)
            val low = code.s4(operands + 4)
            val high = code.s4(operands + 8)
            operands - pc + 12 + (high - low + 1) * 4
        }
        LOOKUPSWITCH -> {
            val operands = pc + 1 + switchPadding(pc)
            operands - pc + 8 + code.s4(operands + 4) * 8
        }
        WIDE -> if (code.u1(pc + 1) == IINC) 6 else 4
        else -> fixedInstructionLength(opcode)
    }

    private fun switchPadding(pc: Int) = (4 - (pc + 1) % 4) % 4

    private fun fixedInstructionLength(opcode: Int): Int = when (opcode) {
        in 0x00..0x0f, in 0x1a..0x35, in 0x3b..0x83, in 0x85..0x98, in 0xac..0xb1, 0xbe, 0xbf, 0xc2, 0xc3 -> 1
        0x10, LDC, in 0x15..0x19, in 0x36..0x3a, 0xa9, 0xbc -> 2
        0x11, LDC_W, 0x14, IINC, in 0x99..0xa8, in 0xb2..0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7 -> 3
        0xc5 -> 4
        0xb9, 0xba, 0xc8, 0xc9 -> 5
        else -> error("Unknown opcode $opcode")
    }

    private fun ByteArray.u1(index: Int) = this[index].toInt() and 0xff

    private fun ByteArray.u2(index: Int) = (u1(index) shl 8) or u1(index + 1)

    private fun ByteArray.s4(index: Int) = ByteBuffer.wrap(this).getInt(index)

    private const val MAGIC_AND_VERSION_BYTES = 8
    private const val ACCESS_THIS_SUPER_BYTES = 6
    private const val MEMBER_HEADER_BYTES = 6
    private const val MAX_STACK_AND_LOCALS_BYTES = 4
    private const val U2_BYTES = 2
    private const val CODE_ATTRIBUTE = "Code"

    private const val TAG_UTF8 = 1
    private const val TAG_INTEGER = 3
    private const val TAG_FLOAT = 4
    private const val TAG_LONG = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_CLASS = 7
    private const val TAG_STRING = 8
    private const val TAG_FIELD_REF = 9
    private const val TAG_METHOD_REF = 10
    private const val TAG_INTERFACE_METHOD_REF = 11
    private const val TAG_NAME_AND_TYPE = 12
    private const val TAG_METHOD_HANDLE = 15
    private const val TAG_METHOD_TYPE = 16
    private const val TAG_DYNAMIC = 17
    private const val TAG_INVOKE_DYNAMIC = 18
    private const val TAG_MODULE = 19
    private const val TAG_PACKAGE = 20

    private const val LDC = 0x12
    private const val LDC_W = 0x13
    private const val IINC = 0x84
    private const val TABLESWITCH = 0xaa
    private const val LOOKUPSWITCH = 0xab
    private const val WIDE = 0xc4
}
