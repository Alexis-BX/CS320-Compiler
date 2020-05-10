package amyc
package codegen

import java.io.ObjectInputStream.GetField

import ast.Identifier
import wasm.{Function, Instructions}
import wasm.Instructions._

import scala.collection.immutable.Range

// Utilities for CodeGen
object Utils {

  // The index of the global variable that represents the free memory boundary
  val memoryBoundary: Int = 0
  // # of global variables
  val globalsNo = 1

  // The default imports we will pass to a wasm Module
  val defaultImports: List[String] = List(
    "\"system\" \"printInt\" (func $Std_printInt (param i32) (result i32))",
    "\"system\" \"printString\" (func $Std_printString (param i32) (result i32))",
    "\"system\" \"readString0\" (func $js_readString0 (param i32) (result i32))",
    "\"system\" \"readInt\" (func $Std_readInt (result i32))",
    "\"system\" \"mem\" (memory 100)"
  )

  // We don't generate code for these functions in CodeGen (they are hard-coded here or in js wrapper)
  val builtInFunctions: Set[String] = Set(
    "Std_printInt",
    "Std_printString",
    "Std_digitToString",
    "Std_readInt",
    "Std_readString",
    "Std_charToString",
    "Std_min",
    "Std_max",
    "Std_clip",
    "Std_abs",
    "S_length",
    "S_charAt",
    "S_strcpy",
    "S_asciiCode",
    "S_asciiToChar"
  )


  /** Utilities */
  // A globally unique name for definitions
  def fullName(owner: Identifier, df: Identifier): String = owner.name + "_" + df.name

  // Given a pointer to an ADT on the top of the stack,
  // will point at its field in index (and consume the ADT).
  // 'index' MUST be 0-based.
  def adtField(index: Int): Code = {
    Const(4 * (index + 1)) <:> Add
  }

  // Increment a local variable
  def incr(local: Int): Code = {
    GetLocal(local) <:> Const(1) <:> Add <:> SetLocal(local)
  }

  def decr(local: Int): Code = {
    GetLocal(local) <:> Const(1) <:> Sub <:> SetLocal(local)
  }

  // A fresh label name
  def getFreshLabel(name: String = "label") = {
    Identifier.fresh(name).fullName
  }

  // Creates a known string constant s in memory
  def mkString(s: String): Code = {
    val size = s.length
    val padding = 4 - size % 4

    val completeS = s + 0.toChar.toString * padding

    val setChars = for ((c, ind) <- completeS.zipWithIndex.toList) yield {
      GetGlobal(memoryBoundary) <:> Const(ind) <:> Add <:>
        Const(c.toInt) <:> Store8
    }

    val setMemory = GetGlobal(memoryBoundary) <:> // Address of first character, length is 4 byte before
      GetGlobal(memoryBoundary) <:>
      Const(size + padding) <:>
      Add <:>
      SetGlobal(memoryBoundary)

    val setSize = GetGlobal(memoryBoundary) <:>
      Const(size) <:>
      Store <:>
      GetGlobal(memoryBoundary) <:>
      Const(4) <:>
      Add <:>
      SetGlobal(memoryBoundary)

    setSize <:> setChars <:> setMemory
  }


  val lengthImpl: Function = {
    Function("S_length", 1, false) { _ =>
      GetLocal(0) <:>
        Const(4) <:>
        Sub <:>
        Load
    }
  }

  val absImpl: Function = {
    Function("Std_abs", 1, false) { _ =>
      GetLocal(0) <:>
      Const(0) <:>
      Lt_s <:>
      If_i32 <:>
      Const(0) <:>
      GetLocal(0) <:>
      Sub <:>
      Else <:>
      GetLocal(0) <:>
      End

    }
  }

  // Returns the max between arg0 and arg1 on stack
  val maxImpl: Function = {
    Function("Std_max", 2, false) { _ =>
      GetLocal(0) <:>
        GetLocal(1) <:>
        Le_s <:>
        If_i32 <:>
        GetLocal(1) <:>
        Else <:>
        GetLocal(0) <:>
        End
    }
  }

  // Returns the min between arg0 and arg1 on stack
  val minImpl: Function = {
    Function("Std_min", 2, false) { _ =>
      GetLocal(0) <:>
        GetLocal(1) <:>
        Le_s <:>
        If_i32 <:>
        GetLocal(0) <:>
        Else <:>
        GetLocal(1) <:>
        End
    }
  }

  /*
    * Ensure that slicing parameters is in bound
    * Arg 0: Index
    * Arg 1: Bound
   */
  val clipImpl: Function = {
    Function("Std_clip", 2, false) { _ =>

      GetLocal(0) <:>
        Const(0) <:>
        Lt_s <:>
        If_void <:>
        // Try to get index in bounds (|index| < bound, it's an index in the string different to zero)
        GetLocal(0) <:>
        GetLocal(1) <:>
        Add <:>
        SetLocal(0) <:>
        Else <:>
        End <:>
        GetLocal(0) <:>
        Const(0) <:>
        Call("Std_max") <:>
        GetLocal(1) <:>
        Call("Std_min")
    }
  }

  def error(msg: String): Code = mkString(s"Error: $msg") <:>
    Call("Std_printString") <:> Unreachable

  /*
* Arguments:
* Arg 0 : Reference to sliced string
* Arg 1 : Start character's position (included)
* Arg 2 : Last character's position (excluded)
* Arg 3 : Step
* Arg 4 : isStartDefault
* Arg 5 : isEndDefault
*/
  val strSliceImpl: Function = {
    Function("S_Slice", 6, false) { lh =>
      // Represents the range of the slicing (would be length if step is 1)
      val rangeLen = lh.getFreshLocal()
      val stringLengthCode: Code = GetLocal(0) <:> Call("S_length")

      GetLocal(3) <:>
        Eqz <:> // step == 0
        If_void <:>
          error("slice step cannot be zero") <:>
        Else <:>
        End <:>
        Const(0) <:>
        GetLocal(3) <:>
        Lt_s <:> // 0 < step
        If_void <:>
          // Case with positive step
          GetLocal(4) <:> // start is default
          If_void <:>
            Const(0) <:>
            SetLocal(1) <:>
          Else <:>
            // If start is defined, validate it
            GetLocal(1) <:>
            stringLengthCode <:>
            Call("Std_clip") <:>
            SetLocal(1) <:>
          End <:>
          // Compute rangeLength
          GetLocal(5) <:>
          If_void <:>
            // If end is default:
            stringLengthCode <:>
            GetLocal(1) <:>
            Sub <:>
            SetLocal(rangeLen) <:>
          Else <:>
            // Clip end
            GetLocal(2) <:>
            stringLengthCode <:>
            Call("Std_clip") <:>
            // Update end
            SetLocal(2) <:>
            Const(0) <:>
            GetLocal(2) <:>
            GetLocal(1) <:>
            Sub <:>
            // rangeLen is max(0, end - start)
            Call("Std_max") <:>
            SetLocal(rangeLen) <:>
          End <:>
        Else <:>
          // Negative step
          GetLocal(4) <:>
          If_void <:>
            // If start is default
            stringLengthCode <:>
            Const(1) <:>
            Sub <:>
            SetLocal(1) <:>
          Else <:>
            // If start has a defined value
            GetLocal(1) <:>
            stringLengthCode <:>
            Const(1) <:>
            Sub <:>
            Call("Std_clip") <:>
            SetLocal(1) <:>
          End <:>
          // Compute rangeLen and end parameters
          GetLocal(5) <:>
          If_void <:>
            // If end is default
            GetLocal(1) <:>
            Const(1) <:>
            Add <:>
            SetLocal(rangeLen) <:>
          Else <:>
            // Else compute a valid value and bound it
            GetLocal(2) <:>
            stringLengthCode <:>
            Call("Std_clip") <:>
            SetLocal(2) <:>
            Const(0) <:>
            GetLocal(1) <:>
            GetLocal(2) <:>
            Sub <:> // start - end
            Call("Std_max") <:>
            SetLocal(rangeLen) <:>
          End <:>
        //
        End <:>
        // Now parameters start, length and step are correct.
        GetLocal(0) <:>
        GetLocal(1) <:>
        Add <:> // Leaves address of first character on stack
        // Must compute length of new string
        GetLocal(rangeLen) <:>
        GetLocal(3) <:>
        Call("Std_abs") <:>
        Div <:> // Integer division left on stack, must add 1 if reminder > 0
        GetLocal(rangeLen) <:>
        GetLocal(3) <:>
        Rem <:>
        Eqz <:>
        If_i32 <:>
          Const(0) <:>
        Else <:>
          Const(1) <:>
        End <:>
        Add <:> // Leave new length on stack
        GetLocal(3) <:>
        // Finally call copy
        Call("S_strcpy")
    }
  }

  val charAtImpl: Function = {
    Function("S_charAt", 2, false) { _ =>
      // Arg 0: reference to string
      // Arg 1: position of access

      // Round index in bounds (same convention as slicing)
      GetLocal(1) <:>
      GetLocal(0) <:>
      Call("S_length") <:>
      Call("Std_clip") <:>
      // Additionnal condition: clip to length -1
      GetLocal(0) <:>
      Call("S_length") <:>
      Const(1) <:>
      Sub <:>
      Call("Std_min") <:>
      // Address of character
      GetLocal(0) <:>
      Add <:>
      Load8_u // Load character on stack and return
    }
  }

  val charToStringImpl: Function = {
    Function("Std_charToString", 1, false) { _ =>
      // Arg 0: character to turn into a String
      // Need 2x32bits for a 1-character String

      def charAddress(i: Int): Code = GetGlobal(memoryBoundary) <:>
      Const(4 + i) <:>
      Add

      def storeZeroAt(i: Int): Code = charAddress(i) <:>
      Const(0) <:>
      Store8

      GetGlobal(memoryBoundary) <:>
      Const(1) <:>
      Store <:>
      GetGlobal(memoryBoundary) <:>
      Const(4) <:>
      Add <:>
      GetLocal(0) <:>
      Store8 <:>
        ((for {i <- List(1, 2, 3)} yield storeZeroAt(i)) reduce(_ <:> _)) <:>
      GetGlobal(memoryBoundary) <:>
      Const(4) <:>
      Add <:>
      GetGlobal(memoryBoundary) <:>
      Const(8) <:>
      Add <:>
      SetGlobal(memoryBoundary)
    }
  }

  val strcpyImpl: Function = {
    Function("S_strcpy", 3, false) { lh =>
      // Assumes that that bounds are valid (checked by caller)
      // Arg 0 : Start of substring
      // Arg 1 : length of new string
      // Arg 2 : step

      val mainLoopLabel = getFreshLabel("mainLoop")
      val paddingLoopLabel = getFreshLabel("paddingLoop")
      val ptrD = lh.getFreshLocal()
      val paddingRef = lh.getFreshLocal()

      GetGlobal(memoryBoundary) <:>
        // Save start of new string in variable
        SetLocal(ptrD) <:>
        // Compute padding length of new String and save it for later
        Const(4) <:>
        GetLocal(1) <:>
        Const(4) <:>
        Rem <:>
        Sub <:>
        SetLocal(paddingRef) <:>
        // Leave ref to new string on stack for return
        GetGlobal(memoryBoundary) <:>
        Const(4) <:>
        Add <:>
        // Compute total length of new String & Make room for new string
        GetGlobal(memoryBoundary) <:>
        GetLocal(1) <:>
        Add <:>
        GetLocal(paddingRef) <:>
        Add <:>
        // String's length
        Const(4) <:>
        Add <:>
        SetGlobal(memoryBoundary) <:> // Update memory boundary to next free address
        GetLocal(ptrD) <:>
        GetLocal(1) <:>
        Store <:> // Store length of new String
        GetLocal(ptrD) <:>
        Const(4) <:>
        Add <:>
        SetLocal(ptrD) <:>
        // Actual copy of the subString
        Loop(mainLoopLabel) <:>
        GetLocal(1) <:>
        Const(0) <:>
        Le_s <:>
        If_void <:>
        Else <:>
        GetLocal(ptrD) <:>
        GetLocal(0) <:>
        Load8_u <:>
        Store8 <:>
        incr(ptrD) <:>
        GetLocal(0) <:> GetLocal(2) <:> Add <:> SetLocal(0) <:>
        decr(1) <:>
        Br(mainLoopLabel) <:>
        End <:>
        End <:>
        // Add padding
        Loop(paddingLoopLabel) <:>
        GetLocal(paddingRef) <:>
        Eqz <:>
        If_void <:>
        Else <:>
        GetLocal(ptrD) <:>
        Const(0) <:>
        Store8 <:>
        incr(ptrD) <:>
        decr(paddingRef) <:>
        Br(paddingLoopLabel) <:>
        End <:>
        End
    }
  }

  // Built-in implementation of concatenation
  val concatImpl: Function = {
    Function("String_concat", 2, false) { lh =>
      val ptrS = lh.getFreshLocal()
      val ptrD = lh.getFreshLocal()
      val label = getFreshLabel()

      def mkLoop: Code = {
        val label = getFreshLabel()
        Loop(label) <:>
          // Load current character
          GetLocal(ptrS) <:> Load8_u <:>
          // If != 0
          If_void <:>
          // Copy to destination
          GetLocal(ptrD) <:>
          GetLocal(ptrS) <:> Load8_u <:>
          Store8 <:>
          // Increment pointers
          incr(ptrD) <:> incr(ptrS) <:>
          // Jump to loop
          Br(label) <:>
          Else <:>
          End <:>
          End
      }
        // First, store length of new string in front
        GetGlobal(memoryBoundary) <:>
        GetLocal(0) <:>
        Call("S_length") <:>
        GetLocal(1) <:>
        Call("S_length") <:>
        Add <:>
        Store <:>
        GetGlobal(memoryBoundary) <:>
        Const(4) <:>
        Add <:>
        SetGlobal(memoryBoundary) <:>
        // Instantiate ptrD to previous memory, ptrS to first string
        GetGlobal(memoryBoundary) <:>
        SetLocal(ptrD) <:>
        GetLocal(0) <:>
        SetLocal(ptrS) <:>
        // Copy first string
        mkLoop <:>
        // Set ptrS to second string
        GetLocal(1) <:>
        SetLocal(ptrS) <:>
        // Copy second string
        mkLoop <:>
        //
        // Pad with zeros until multiple of 4
        //
        Loop(label) <:>
        // Write 0
        GetLocal(ptrD) <:> Const(0) <:> Store8 <:>
        // Check if multiple of 4
        GetLocal(ptrD) <:> Const(4) <:> Rem <:>
        // If not
        If_void <:>
        // Increment pointer and go back
        incr(ptrD) <:>
        Br(label) <:>
        Else <:>
        End <:>
        End <:>
        //
        // Put string pointer to stack, set new memory boundary and return
        GetGlobal(memoryBoundary) <:> GetLocal(ptrD) <:> Const(1) <:> Add <:> SetGlobal(memoryBoundary)
    }
  }

  val digitToStringImpl: Function = {
    Function("Std_digitToString", 1, false) { _ =>
      // We know we have to create a string of total size 4 (digit code + padding), so we do it all together
      // We do not need to shift the digit due to little endian structure!
      GetGlobal(memoryBoundary) <:> GetLocal(0) <:> Const('0'.toInt) <:> Add <:> Store <:>
        // Load memory boundary to stack, then move it by 4
        GetGlobal(memoryBoundary) <:>
        GetGlobal(memoryBoundary) <:> Const(4) <:> Add <:> SetGlobal(memoryBoundary)
    }
  }

  val readStringImpl: Function = {
    Function("Std_readString", 0, false) { _ =>
      // We need to use the weird interface of javascript read string:
      // we pass the old memory boundary and get the new one.
      // In the end we have to return the old, where the fresh string lies.
      GetGlobal(memoryBoundary) <:> GetGlobal(memoryBoundary) <:> Call("js_readString0") <:>
        SetGlobal(memoryBoundary)
    }
  }

  val asciiCodeImpl: Function = {
    Function("S_asciiCode", 1, false) {_ =>
      GetLocal(0)
    }
  }

  val asciiToCharImpl: Function = {
    Function("S_asciiToChar", 1, false) { _ =>
      GetLocal(0)
    }
  }


  val wasmFunctions = List(concatImpl, digitToStringImpl, readStringImpl, clipImpl, lengthImpl, strcpyImpl, strSliceImpl, maxImpl, minImpl, absImpl, charAtImpl, charToStringImpl, asciiCodeImpl, asciiToCharImpl)

}
