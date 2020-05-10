
object S {

  def length(s: String): Int = {
    error("") // Stub implementation
  }

  def equalsAcc(s1: String, s2: String, i: Int, length: Int): Boolean = {
    if (i == length) {
      true
    } else {
      if (charAt(s1, i) == charAt(s2, i)) {
        equalsAcc(s1, s2, i + 1, length)
      } else {
        false
      }
    }
  }

  def equals(s1: String, s2: String): Boolean = {
    if (length(s1) == length(s2)) {
      equalsAcc(s1, s2, 0, length(s1))
    } else {
      false
    }
  }

  def charAt(s: String, i: Int): Char = {
    error("") // Stub
  }

  def asciiCode(c: Char): Int = {
    error("") // Stub
  }

  def asciiToChar(i: Int): Char = {
    error("") // Stub
  }

  def firstChar(s: String, c: Char, from: Int, to: Int, step: Int): Int = {
    if((from == to) || !(charAt(s, from) == c)) {
      from
    } else {
      firstChar(s, c , from + step, to, step)
    }
  }

  def strip(s: String): String = {

    val first: Int = firstChar(s, ' ', 0, length(s), 1);
    val sec: Int = firstChar(s, ' ', length(s) - 1, 0, -1);

    s[first: sec + 1]
  }

  def replaceAcc(s: String, toReplace: String, pattern: String, resultAcc: String, currPos: Int, lastPos: Int): String = {
    // Stop condition
    if(length(s) <= currPos) {
      resultAcc ++ s[lastPos: currPos]
    } else {
      if(equals(s[currPos: currPos + length(toReplace)], toReplace)) {
        replaceAcc(s, toReplace, pattern, resultAcc ++ s[lastPos: currPos] ++ pattern, currPos + length(toReplace), currPos + length(toReplace))
      } else {
        replaceAcc(s, toReplace, pattern, resultAcc, currPos + 1, lastPos)
      }
    }
  }

  def replace(s: String, toReplace: String, pattern: String): String = {
    if(length(toReplace) == 0) {
      s
    } else {
      replaceAcc(s, toReplace, pattern, "",0, 0)
    }
  }


  def cipherAcc(s: String, offset: Int, acc: String, currPos: Int, charOffset: Int): String = {
    if(currPos == length(s)) {
      acc
    } else {
      val c: Char = charAt(s, currPos);
      val asciiOffset: Int = ((asciiCode(c) - charOffset + offset) % 26 + 26) % 26;
      val ascii: Int = charOffset + asciiOffset;
      cipherAcc(s, offset, acc ++ Std.charToString(asciiToChar(ascii)), currPos + 1, charOffset)
    }
  }

  def caesarCipher(plainText: String, offset: Int, upper: Boolean): String = {
    val charOffset: Int = (if(upper) {
      asciiCode('A')
    } else {
      asciiCode('a')
    });
    cipherAcc(plainText, offset, "", 0, charOffset)
  }

  def caesarDecipher(cipherText: String, offset: Int, upper: Boolean): String = {
    val charOffset: Int = (if(upper) {
      asciiCode('A')
    } else {
      asciiCode('a')
    });
    cipherAcc(cipherText, -offset, "", 0, charOffset)
  }

/*
  def splitAcc(s: String, lastPos: Int, currPos: Int, acc: List, separator: Char): S.List = {
    // Stop condition
    if(currPos == length(s)) {
      //If the last string is empty, return acc
      if(lastPos + 1 == currPos) {
        acc
      } else {
        S.reverse(S.Cons(s[lastPos: currPos], acc))
      }
    } else {
      // If need to split
      if(charAt(s, currPos) == separator) {
        // Could be empty string if 2 separators follow each other
        Nil()//splitAcc(s, currPos + 1, currPos + 1, S.Cons())
      } else {
        Nil()
      }
    }
  }

  def split(s: String, separator: Char): S.List = {
    splitAcc(s, 0, 0, S.Nil(), separator)
  }
  */

}