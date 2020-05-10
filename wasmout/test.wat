(module 
  (import "system" "printInt" (func $Std_printInt (param i32) (result i32)))
  (import "system" "printString" (func $Std_printString (param i32) (result i32)))
  (import "system" "readString0" (func $js_readString0 (param i32) (result i32)))
  (import "system" "readInt" (func $Std_readInt (result i32)))
  (import "system" "mem" (memory 100))
  (global (mut i32) i32.const 0) 

  (func $String_concat (param i32 i32) (result i32) (local i32 i32)
    get_global 0
    get_local 0
    call $S_length
    get_local 1
    call $S_length
    i32.add
    i32.store
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    get_global 0
    set_local 3
    get_local 0
    set_local 2
    loop $label_1
      get_local 2
      i32.load8_u
      if
        get_local 3
        get_local 2
        i32.load8_u
        i32.store8
        get_local 3
        i32.const 1
        i32.add
        set_local 3
        get_local 2
        i32.const 1
        i32.add
        set_local 2
        br $label_1
      else
      end
    end
    get_local 1
    set_local 2
    loop $label_2
      get_local 2
      i32.load8_u
      if
        get_local 3
        get_local 2
        i32.load8_u
        i32.store8
        get_local 3
        i32.const 1
        i32.add
        set_local 3
        get_local 2
        i32.const 1
        i32.add
        set_local 2
        br $label_2
      else
      end
    end
    loop $label_0
      get_local 3
      i32.const 0
      i32.store8
      get_local 3
      i32.const 4
      i32.rem_s
      if
        get_local 3
        i32.const 1
        i32.add
        set_local 3
        br $label_0
      else
      end
    end
    get_global 0
    get_local 3
    i32.const 1
    i32.add
    set_global 0
  )

  (func $Std_digitToString (param i32) (result i32) 
    get_global 0
    get_local 0
    i32.const 48
    i32.add
    i32.store
    get_global 0
    get_global 0
    i32.const 4
    i32.add
    set_global 0
  )

  (func $Std_readString (result i32) 
    get_global 0
    get_global 0
    call $js_readString0
    set_global 0
  )

  (func $Std_clip (param i32 i32) (result i32) 
    get_local 0
    i32.const 0
    i32.lt_s
    if
      get_local 0
      get_local 1
      i32.add
      set_local 0
    else
    end
    get_local 0
    i32.const 0
    call $Std_max
    get_local 1
    call $Std_min
  )

  (func $S_length (param i32) (result i32) 
    get_local 0
    i32.const 4
    i32.sub
    i32.load
  )

  (func $S_strcpy (param i32 i32 i32) (result i32) (local i32 i32)
    get_global 0
    set_local 3
    i32.const 4
    get_local 1
    i32.const 4
    i32.rem_s
    i32.sub
    set_local 4
    get_global 0
    i32.const 4
    i32.add
    get_global 0
    get_local 1
    i32.add
    get_local 4
    i32.add
    i32.const 4
    i32.add
    set_global 0
    get_local 3
    get_local 1
    i32.store
    get_local 3
    i32.const 4
    i32.add
    set_local 3
    loop $mainLoop_0
      get_local 1
      i32.const 0
      i32.le_s
      if
      else
        get_local 3
        get_local 0
        i32.load8_u
        i32.store8
        get_local 3
        i32.const 1
        i32.add
        set_local 3
        get_local 0
        get_local 2
        i32.add
        set_local 0
        get_local 1
        i32.const 1
        i32.sub
        set_local 1
        br $mainLoop_0
      end
    end
    loop $paddingLoop_0
      get_local 4
      i32.eqz
      if
      else
        get_local 3
        i32.const 0
        i32.store8
        get_local 3
        i32.const 1
        i32.add
        set_local 3
        get_local 4
        i32.const 1
        i32.sub
        set_local 4
        br $paddingLoop_0
      end
    end
  )

  (func $S_Slice (param i32 i32 i32 i32 i32 i32) (result i32) (local i32)
    get_local 3
    i32.eqz
    if
      get_global 0
      i32.const 32
      i32.store
      get_global 0
      i32.const 4
      i32.add
      set_global 0
      get_global 0
      i32.const 0
      i32.add
      i32.const 69
      i32.store8
      get_global 0
      i32.const 1
      i32.add
      i32.const 114
      i32.store8
      get_global 0
      i32.const 2
      i32.add
      i32.const 114
      i32.store8
      get_global 0
      i32.const 3
      i32.add
      i32.const 111
      i32.store8
      get_global 0
      i32.const 4
      i32.add
      i32.const 114
      i32.store8
      get_global 0
      i32.const 5
      i32.add
      i32.const 58
      i32.store8
      get_global 0
      i32.const 6
      i32.add
      i32.const 32
      i32.store8
      get_global 0
      i32.const 7
      i32.add
      i32.const 115
      i32.store8
      get_global 0
      i32.const 8
      i32.add
      i32.const 108
      i32.store8
      get_global 0
      i32.const 9
      i32.add
      i32.const 105
      i32.store8
      get_global 0
      i32.const 10
      i32.add
      i32.const 99
      i32.store8
      get_global 0
      i32.const 11
      i32.add
      i32.const 101
      i32.store8
      get_global 0
      i32.const 12
      i32.add
      i32.const 32
      i32.store8
      get_global 0
      i32.const 13
      i32.add
      i32.const 115
      i32.store8
      get_global 0
      i32.const 14
      i32.add
      i32.const 116
      i32.store8
      get_global 0
      i32.const 15
      i32.add
      i32.const 101
      i32.store8
      get_global 0
      i32.const 16
      i32.add
      i32.const 112
      i32.store8
      get_global 0
      i32.const 17
      i32.add
      i32.const 32
      i32.store8
      get_global 0
      i32.const 18
      i32.add
      i32.const 99
      i32.store8
      get_global 0
      i32.const 19
      i32.add
      i32.const 97
      i32.store8
      get_global 0
      i32.const 20
      i32.add
      i32.const 110
      i32.store8
      get_global 0
      i32.const 21
      i32.add
      i32.const 110
      i32.store8
      get_global 0
      i32.const 22
      i32.add
      i32.const 111
      i32.store8
      get_global 0
      i32.const 23
      i32.add
      i32.const 116
      i32.store8
      get_global 0
      i32.const 24
      i32.add
      i32.const 32
      i32.store8
      get_global 0
      i32.const 25
      i32.add
      i32.const 98
      i32.store8
      get_global 0
      i32.const 26
      i32.add
      i32.const 101
      i32.store8
      get_global 0
      i32.const 27
      i32.add
      i32.const 32
      i32.store8
      get_global 0
      i32.const 28
      i32.add
      i32.const 122
      i32.store8
      get_global 0
      i32.const 29
      i32.add
      i32.const 101
      i32.store8
      get_global 0
      i32.const 30
      i32.add
      i32.const 114
      i32.store8
      get_global 0
      i32.const 31
      i32.add
      i32.const 111
      i32.store8
      get_global 0
      i32.const 32
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 33
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 34
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 35
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      get_global 0
      i32.const 36
      i32.add
      set_global 0
      call $Std_printString
      unreachable
    else
    end
    i32.const 0
    get_local 3
    i32.lt_s
    if
      get_local 4
      if
        i32.const 0
        set_local 1
      else
        get_local 1
        get_local 0
        call $S_length
        call $Std_clip
        set_local 1
      end
      get_local 5
      if
        get_local 0
        call $S_length
        get_local 1
        i32.sub
        set_local 6
      else
        get_local 2
        get_local 0
        call $S_length
        call $Std_clip
        set_local 2
        i32.const 0
        get_local 2
        get_local 1
        i32.sub
        call $Std_max
        set_local 6
      end
    else
      get_local 4
      if
        get_local 0
        call $S_length
        i32.const 1
        i32.sub
        set_local 1
      else
        get_local 1
        get_local 0
        call $S_length
        i32.const 1
        i32.sub
        call $Std_clip
        set_local 1
      end
      get_local 5
      if
        get_local 1
        i32.const 1
        i32.add
        set_local 6
      else
        get_local 2
        get_local 0
        call $S_length
        call $Std_clip
        set_local 2
        i32.const 0
        get_local 1
        get_local 2
        i32.sub
        call $Std_max
        set_local 6
      end
    end
    get_local 0
    get_local 1
    i32.add
    get_local 6
    get_local 3
    call $Std_abs
    i32.div_s
    get_local 6
    get_local 3
    i32.rem_s
    i32.eqz
    if (result i32)
      i32.const 0
    else
      i32.const 1
    end
    i32.add
    get_local 3
    call $S_strcpy
  )

  (func $Std_max (param i32 i32) (result i32) 
    get_local 0
    get_local 1
    i32.le_s
    if (result i32)
      get_local 1
    else
      get_local 0
    end
  )

  (func $Std_min (param i32 i32) (result i32) 
    get_local 0
    get_local 1
    i32.le_s
    if (result i32)
      get_local 0
    else
      get_local 1
    end
  )

  (func $Std_abs (param i32) (result i32) 
    get_local 0
    i32.const 0
    i32.lt_s
    if (result i32)
      i32.const 0
      get_local 0
      i32.sub
    else
      get_local 0
    end
  )

  (func $S_charAt (param i32 i32) (result i32) 
    get_local 1
    get_local 0
    call $S_length
    call $Std_clip
    get_local 0
    call $S_length
    i32.const 1
    i32.sub
    call $Std_min
    get_local 0
    i32.add
    i32.load8_u
  )

  (func $Std_charToString (param i32) (result i32) 
    get_global 0
    i32.const 1
    i32.store
    get_global 0
    i32.const 4
    i32.add
    get_local 0
    i32.store8
    get_global 0
    i32.const 5
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 6
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 7
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 4
    i32.add
    get_global 0
    i32.const 8
    i32.add
    set_global 0
  )

  (func $S_asciiCode (param i32) (result i32) 
    get_local 0
  )

  (func $S_asciiToChar (param i32) (result i32) 
    get_local 0
  )

  (func $Std_printBoolean (param i32) (result i32) 
    get_local 0
    call $Std_booleanToString
    call $Std_printString
  )

  (func $Std_intToString (param i32) (result i32) (local i32 i32)
    get_local 0
    i32.const 0
    i32.lt_s
    if (result i32)
      get_global 0
      i32.const 1
      i32.store
      get_global 0
      i32.const 4
      i32.add
      set_global 0
      get_global 0
      i32.const 0
      i32.add
      i32.const 45
      i32.store8
      get_global 0
      i32.const 1
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 2
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 3
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      get_global 0
      i32.const 4
      i32.add
      set_global 0
      i32.const 0
      get_local 0
      i32.sub
      call $Std_intToString
      call $String_concat
    else
      get_local 0
      i32.const 10
      i32.rem_s
      set_local 1
      get_local 0
      i32.const 10
      i32.div_s
      set_local 2
      get_local 2
      i32.const 0
      i32.eq
      if (result i32)
        get_local 1
        call $Std_digitToString
      else
        get_local 2
        call $Std_intToString
        get_local 1
        call $Std_digitToString
        call $String_concat
      end
    end
  )

  (func $Std_booleanToString (param i32) (result i32) 
    get_local 0
    if (result i32)
      get_global 0
      i32.const 4
      i32.store
      get_global 0
      i32.const 4
      i32.add
      set_global 0
      get_global 0
      i32.const 0
      i32.add
      i32.const 116
      i32.store8
      get_global 0
      i32.const 1
      i32.add
      i32.const 114
      i32.store8
      get_global 0
      i32.const 2
      i32.add
      i32.const 117
      i32.store8
      get_global 0
      i32.const 3
      i32.add
      i32.const 101
      i32.store8
      get_global 0
      i32.const 4
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 5
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 6
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 7
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      get_global 0
      i32.const 8
      i32.add
      set_global 0
    else
      get_global 0
      i32.const 5
      i32.store
      get_global 0
      i32.const 4
      i32.add
      set_global 0
      get_global 0
      i32.const 0
      i32.add
      i32.const 102
      i32.store8
      get_global 0
      i32.const 1
      i32.add
      i32.const 97
      i32.store8
      get_global 0
      i32.const 2
      i32.add
      i32.const 108
      i32.store8
      get_global 0
      i32.const 3
      i32.add
      i32.const 115
      i32.store8
      get_global 0
      i32.const 4
      i32.add
      i32.const 101
      i32.store8
      get_global 0
      i32.const 5
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 6
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 7
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      get_global 0
      i32.const 8
      i32.add
      set_global 0
    end
  )

  (func $S_equalsAcc (param i32 i32 i32 i32) (result i32) 
    get_local 2
    get_local 3
    i32.eq
    if (result i32)
      i32.const 1
    else
      get_local 0
      get_local 2
      call $S_charAt
      get_local 1
      get_local 2
      call $S_charAt
      i32.eq
      if (result i32)
        get_local 0
        get_local 1
        get_local 2
        i32.const 1
        i32.add
        get_local 3
        call $S_equalsAcc
      else
        i32.const 0
      end
    end
  )

  (func $S_equals (param i32 i32) (result i32) 
    get_local 0
    call $S_length
    get_local 1
    call $S_length
    i32.eq
    if (result i32)
      get_local 0
      get_local 1
      i32.const 0
      get_local 0
      call $S_length
      call $S_equalsAcc
    else
      i32.const 0
    end
  )

  (func $S_firstChar (param i32 i32 i32 i32 i32) (result i32) 
    get_local 2
    get_local 3
    i32.eq
    if (result i32)
      i32.const 1
    else
      get_local 0
      get_local 2
      call $S_charAt
      get_local 1
      i32.eq
      i32.eqz
    end
    if (result i32)
      get_local 2
    else
      get_local 0
      get_local 1
      get_local 2
      get_local 4
      i32.add
      get_local 3
      get_local 4
      call $S_firstChar
    end
  )

  (func $S_strip (param i32) (result i32) (local i32 i32)
    get_local 0
    i32.const 32
    i32.const 0
    get_local 0
    call $S_length
    i32.const 1
    call $S_firstChar
    set_local 1
    get_local 0
    i32.const 32
    get_local 0
    call $S_length
    i32.const 1
    i32.sub
    i32.const 0
    i32.const 0
    i32.const 1
    i32.sub
    call $S_firstChar
    set_local 2
    get_local 0
    get_local 1
    get_local 2
    i32.const 1
    i32.add
    i32.const 1
    i32.const 0
    i32.const 0
    call $S_Slice
  )

  (func $S_replaceAcc (param i32 i32 i32 i32 i32 i32) (result i32) 
    get_local 0
    call $S_length
    get_local 4
    i32.le_s
    if (result i32)
      get_local 3
      get_local 0
      get_local 5
      get_local 4
      i32.const 1
      i32.const 0
      i32.const 0
      call $S_Slice
      call $String_concat
    else
      get_local 0
      get_local 4
      get_local 4
      get_local 1
      call $S_length
      i32.add
      i32.const 1
      i32.const 0
      i32.const 0
      call $S_Slice
      get_local 1
      call $S_equals
      if (result i32)
        get_local 0
        get_local 1
        get_local 2
        get_local 3
        get_local 0
        get_local 5
        get_local 4
        i32.const 1
        i32.const 0
        i32.const 0
        call $S_Slice
        call $String_concat
        get_local 2
        call $String_concat
        get_local 4
        get_local 1
        call $S_length
        i32.add
        get_local 4
        get_local 1
        call $S_length
        i32.add
        call $S_replaceAcc
      else
        get_local 0
        get_local 1
        get_local 2
        get_local 3
        get_local 4
        i32.const 1
        i32.add
        get_local 5
        call $S_replaceAcc
      end
    end
  )

  (func $S_replace (param i32 i32 i32) (result i32) 
    get_local 1
    call $S_length
    i32.const 0
    i32.eq
    if (result i32)
      get_local 0
    else
      get_local 0
      get_local 1
      get_local 2
      get_global 0
      i32.const 0
      i32.store
      get_global 0
      i32.const 4
      i32.add
      set_global 0
      get_global 0
      i32.const 0
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 1
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 2
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      i32.const 3
      i32.add
      i32.const 0
      i32.store8
      get_global 0
      get_global 0
      i32.const 4
      i32.add
      set_global 0
      i32.const 0
      i32.const 0
      call $S_replaceAcc
    end
  )

  (func $S_cipherAcc (param i32 i32 i32 i32 i32) (result i32) (local i32 i32 i32)
    get_local 3
    get_local 0
    call $S_length
    i32.eq
    if (result i32)
      get_local 2
    else
      get_local 0
      get_local 3
      call $S_charAt
      set_local 5
      get_local 5
      call $S_asciiCode
      get_local 4
      i32.sub
      get_local 1
      i32.add
      i32.const 26
      i32.rem_s
      i32.const 26
      i32.add
      i32.const 26
      i32.rem_s
      set_local 6
      get_local 4
      get_local 6
      i32.add
      set_local 7
      get_local 0
      get_local 1
      get_local 2
      get_local 7
      call $S_asciiToChar
      call $Std_charToString
      call $String_concat
      get_local 3
      i32.const 1
      i32.add
      get_local 4
      call $S_cipherAcc
    end
  )

  (func $S_caesarCipher (param i32 i32 i32) (result i32) (local i32)
    get_local 2
    if (result i32)
      i32.const 65
      call $S_asciiCode
    else
      i32.const 97
      call $S_asciiCode
    end
    set_local 3
    get_local 0
    get_local 1
    get_global 0
    i32.const 0
    i32.store
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    get_global 0
    i32.const 0
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 1
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 2
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 3
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    i32.const 0
    get_local 3
    call $S_cipherAcc
  )

  (func $S_caesarDecipher (param i32 i32 i32) (result i32) (local i32)
    get_local 2
    if (result i32)
      i32.const 65
      call $S_asciiCode
    else
      i32.const 97
      call $S_asciiCode
    end
    set_local 3
    get_local 0
    i32.const 0
    get_local 1
    i32.sub
    get_global 0
    i32.const 0
    i32.store
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    get_global 0
    i32.const 0
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 1
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 2
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 3
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    i32.const 0
    get_local 3
    call $S_cipherAcc
  )
  (export "test_main" (func $test_main))
  (func $test_main (local i32)
    get_global 0
    i32.const 29
    i32.store
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    get_global 0
    i32.const 0
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 1
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 2
    i32.add
    i32.const 105
    i32.store8
    get_global 0
    i32.const 3
    i32.add
    i32.const 115
    i32.store8
    get_global 0
    i32.const 4
    i32.add
    i32.const 32
    i32.store8
    get_global 0
    i32.const 5
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 6
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 7
    i32.add
    i32.const 97
    i32.store8
    get_global 0
    i32.const 8
    i32.add
    i32.const 116
    i32.store8
    get_global 0
    i32.const 9
    i32.add
    i32.const 32
    i32.store8
    get_global 0
    i32.const 10
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 11
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 12
    i32.add
    i32.const 105
    i32.store8
    get_global 0
    i32.const 13
    i32.add
    i32.const 115
    i32.store8
    get_global 0
    i32.const 14
    i32.add
    i32.const 32
    i32.store8
    get_global 0
    i32.const 15
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 16
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 17
    i32.add
    i32.const 97
    i32.store8
    get_global 0
    i32.const 18
    i32.add
    i32.const 116
    i32.store8
    get_global 0
    i32.const 19
    i32.add
    i32.const 32
    i32.store8
    get_global 0
    i32.const 20
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 21
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 22
    i32.add
    i32.const 105
    i32.store8
    get_global 0
    i32.const 23
    i32.add
    i32.const 115
    i32.store8
    get_global 0
    i32.const 24
    i32.add
    i32.const 32
    i32.store8
    get_global 0
    i32.const 25
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 26
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 27
    i32.add
    i32.const 97
    i32.store8
    get_global 0
    i32.const 28
    i32.add
    i32.const 116
    i32.store8
    get_global 0
    i32.const 29
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 30
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 31
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    get_global 0
    i32.const 32
    i32.add
    set_global 0
    get_global 0
    i32.const 4
    i32.store
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    get_global 0
    i32.const 0
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 1
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 2
    i32.add
    i32.const 97
    i32.store8
    get_global 0
    i32.const 3
    i32.add
    i32.const 116
    i32.store8
    get_global 0
    i32.const 4
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 5
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 6
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    i32.const 7
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    get_global 0
    i32.const 8
    i32.add
    set_global 0
    get_global 0
    i32.const 11
    i32.store
    get_global 0
    i32.const 4
    i32.add
    set_global 0
    get_global 0
    i32.const 0
    i32.add
    i32.const 84
    i32.store8
    get_global 0
    i32.const 1
    i32.add
    i32.const 117
    i32.store8
    get_global 0
    i32.const 2
    i32.add
    i32.const 104
    i32.store8
    get_global 0
    i32.const 3
    i32.add
    i32.const 117
    i32.store8
    get_global 0
    i32.const 4
    i32.add
    i32.const 97
    i32.store8
    get_global 0
    i32.const 5
    i32.add
    i32.const 117
    i32.store8
    get_global 0
    i32.const 6
    i32.add
    i32.const 97
    i32.store8
    get_global 0
    i32.const 7
    i32.add
    i32.const 117
    i32.store8
    get_global 0
    i32.const 8
    i32.add
    i32.const 97
    i32.store8
    get_global 0
    i32.const 9
    i32.add
    i32.const 117
    i32.store8
    get_global 0
    i32.const 10
    i32.add
    i32.const 116
    i32.store8
    get_global 0
    i32.const 11
    i32.add
    i32.const 0
    i32.store8
    get_global 0
    get_global 0
    i32.const 12
    i32.add
    set_global 0
    set_local 0
    get_local 0
    i32.const 0
    get_local 0
    call $S_length
    i32.const 2
    i32.const 1
    i32.const 1
    call $S_Slice
    call $S_replace
    call $Std_printString
    drop
  )
)