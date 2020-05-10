object charDemo {
  // 1 character Char literal
  val c0: Char = 'A';
  // Special character Char literal
  val c: Char = '\n';

  // String with return character
  val s: String = "Very special string" ++
    Std.charToString(c);
}