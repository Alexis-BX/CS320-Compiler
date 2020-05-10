package amyc
package parsing

import utils._
import java.io.File

import scallion.lexical._
import scallion.input._
import amyc.utils.Position

// The lexer for Amy.
object Lexer extends Pipeline[List[File], Iterator[Token]]
  with Lexers[Token, Char, SourcePosition] {

  /** Tiny Scallion-lexer reference:
    * ==============================
    * Scallion's lexer essentially allows you to define a list of regular expressions
    * in their order of priority. To tokenize a given input stream of characters, each
    * individual regular expression is applied in turn. If a given expression matches, it
    * is used to produce a token of maximal length. Whenever a regular expression does not
    * match, the expression of next-highest priority is tried.
    * The result is a stream of tokens.
    *
    * Regular expressions `r` can be built using the following operators:
    *   - `word("abc")`  matches the sequence "abc" exactly
    *   - `r1 | r2`      matches either expression `r1` or expression `r2`
    *   - `r1 ~ r2`      matches `r1` followed by `r2`
    *   - `oneOf("xy")`  matches either "x" or "y"
    * (i.e., it is a shorthand of `word` and `|` for single characters)
    *   - `elem(c)`      matches character `c`
    *   - `elem(f)`      matches any character for which the boolean predicate `f` holds 
    *   - `opt(r)`       matches `r` or nothing at all
    *   - `many(r)`      matches any number of repetitions of `r` (including none at all)
    *   - `many1(r)`     matches any non-zero number of repetitions of `r`
    *
    * To define the token that should be output for a given expression, one can use
    * the `|>` combinator with an expression on the left-hand side and a function
    * producing the token on the right. The function is given the sequence of matched
    * characters and the source-position range as arguments.
    *
    * For instance,
    *
    * `elem(_.isDigit) ~ word("kg") |> {
    * (cs, range) => WeightLiteralToken(cs.mkString).setPos(range._1)) }`
    *
    * will match a single digit followed by the characters "kg" and turn them into a
    * "WeightLiteralToken" whose value will be the full string matched (e.g. "1kg").
    */


  import Tokens._


  def checkIntInRange(str: String): Token = {
    if (BigInt(str) > BigInt(Int.MaxValue))
      ErrorToken(s"Overflow error: Int litteral ${str}.")
    else
      IntLitToken(str.toInt)
  }

  private val alpha = elem(c => c.isLetter)
  private val digit = elem(c => c.isDigit)
  private val alphaNum = alpha | digit | elem('_')
  private val stringChar = elem(c => c != '\n' && c != '"')

  private val quoteChar = elem('"')
  // These are the string representation of special characters allowed as char literals
  private val escSingleLit = "\\'"
  private val escBackLit = "\\"
  private val escNewLineLit = "\\n"
  private val escTabLit = "\\t"
  private val escRetLit = "\\r"
  private val delimiter = oneOf(".,:;()[]{}=") | word("=>")
  private val commentChars = many((elem('*') ~ elem(_ != '/')) | elem(_ != '*'))


  val lexer = Lexer(
    // Keywords
    word("abstract") | word("case") | word("class") |
      word("def") | word("else") | word("extends") |
      word("if") | word("match") | word("object") |
      word("val") | word("error") | word("_")
      |> { (cs, range) => KeywordToken(cs.mkString).setPos(range._1) },


    // Primitive type names
    word("Int") | word("String") | word("Boolean") | word("Unit") | word("Char")
      |> { (cs, range) => PrimTypeToken(cs.mkString).setPos(range._1) },

    // Boolean literals
    word("true") | word("false")
      |> { (cs, range) => BoolLitToken(cs.mkString == "true").setPos(range._1) },

    // Char literals. Can be standard or \n \' \" \\ \n \t
    elem('\'') ~ many(word(escSingleLit) | elem(c => c != '\'')) ~ elem('\'')
      |> { (cs, range) =>
      val value = cs.tail.dropRight(1).mkString("")
      (if (value.length == 0) ErrorToken("Error: empty character literal.")
      else if (value.length == 1) CharLitToken(value(0))
      else if (value == escSingleLit) CharLitToken('\'')
      else if (value == escBackLit) CharLitToken('\\')
      else if (value == escNewLineLit) CharLitToken('\n')
      else if (value == escTabLit) CharLitToken('\t')
      else if (value == escRetLit) CharLitToken('\r')
      else if (value.length == 2 && value(0) == '\\') ErrorToken(s"Error: unknown special character '${value}'.")
      else ErrorToken("Error: character literal '" + value + "' is too long.")
        ).setPos(range._1)
    },

    // Operators
    oneOf("+-*/%<") | word("<=") | word("&&")
      | word("||") | word("==") | word("++") | word("!")
      |> { (cs, range) => OperatorToken(cs.mkString).setPos(range._1) },


    // Identifiers
    alpha ~ many(alphaNum) |> { (cs, range) => IdentifierToken(cs.mkString).setPos(range._1) },


    // Integer literals
    // NOTE: Make sure to handle invalid (e.g. overflowing) integer values safely by
    //       emitting an ErrorToken instead.
    many1(digit) |> { (cs, range) =>
      val str = cs.mkString
      val res = if (BigInt(str) > BigInt(Int.MaxValue))
        ErrorToken(s"Overflow error: Int litteral ${str}.")
      else
        IntLitToken(str.toInt)
      res.setPos(range._1)
    },
    // String literals
    quoteChar ~ many(stringChar) ~ quoteChar |> {
      (cs, range) => StringLitToken(cs.filter(_ != '"').mkString).setPos(range._1)
    },
    //Delimiters and whitespace
    many1(elem(c => c == ' ' || c == '\t' || c == '\n')) |> {
      (cs, _) => SpaceToken()
    },

    delimiter |> { (cs, range) => DelimiterToken(cs.mkString).setPos(range._1) },

    // Single line comments
    word("//") ~ many(elem(_ != '\n'))
      |> { cs => CommentToken(cs.mkString("")) },

    // NOTE: Amy does not support nested multi-line comments (e.g. `/* foo /* bar */ */`).
    //       Make sure that unclosed multi-line comments result in an ErrorToken.
    word("/*") ~ commentChars ~ word("*/")
      |> { (cs, range) => CommentToken(cs.mkString).setPos(range._1) },
    word("/*") ~ commentChars
      |> { (cs, range) => ErrorToken("/* unclosed comment.").setPos(range._1) }

  ) onError {
    // We also emit ErrorTokens for Scallion-handled errors.
    (cs, range) => ErrorToken(cs.mkString).setPos(range._1)
  } onEnd {
    // Once all the input has been consumed, we emit one EOFToken.
    pos => EOFToken().setPos(pos)
  }

  override def run(ctx: Context)(files: List[File]): Iterator[Token] = {
    var it = Seq[Token]().toIterator

    for (file <- files) {
      val source = Source.fromFile(file, SourcePositioner(file))
      it ++= lexer.spawn(source).filter {
        token =>
          token match {
            case CommentToken(_) => false
            case SpaceToken() => false
            case _ => true
          }
      }.map {
        case token@ErrorToken(error) => ctx.reporter.fatal("Unknown token at " + token.position + ": " + error)
        case token => token
      }
    }
    it
  }
}

/** Extracts all tokens from input and displays them */
object DisplayTokens extends Pipeline[Iterator[Token], Unit] {
  override def run(ctx: Context)(tokens: Iterator[Token]): Unit = {
    tokens.foreach(println(_))
  }
}
