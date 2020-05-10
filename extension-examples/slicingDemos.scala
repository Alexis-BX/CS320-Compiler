object slicingDemos {
  // Basic slicing. For readability, used literals
  "asdf"[::-1]; // fdsa
  val str: String = "aabbccddee";
  str[::4]; // ace
  str[::-4]; // eca
  str[-3: 2: -2]; // dcb
  str[:1000:4]; // ace

  // Slicing with expressions
  val e0: Char = 'A';
  str[e0 match {
    case 'E' => 0
    case 'A' => 999
  }:: -1]; //  eeddccbbaa
  // Similar test can be conducted with conditional expression

  // Chained slicing
  str[0:5][::-1][2:99] // baa
}