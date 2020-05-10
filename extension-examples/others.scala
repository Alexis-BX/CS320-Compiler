object others {
  val str: String = "aabb";
  S.charAt(str, 1); // a
  S.charAt(str, -1); // b

  S.replace("This That This That This That", "That", "a");
  // This a This a This a

  S.replace("This That This That This That", "That",   "Tuhuauauaut"[::2]);
  // This Thaaat This Thaaat This Thaaat



  val str3: String = "   aabb   ";
  S.strip(str); // aabb

  val plainText: String = "ABCDEFA";
  S.caesarCipher(plainText, 66, true); // OPQRSTO

  S.caesarDecipher(cipherText, 66, true) // ABCDEFA
}
}