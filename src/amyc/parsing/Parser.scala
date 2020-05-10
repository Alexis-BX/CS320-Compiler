package amyc
package parsing

import scala.language.implicitConversions
import amyc.ast.NominalTreeModule._
import amyc.utils._
import Tokens._
import TokenKinds._
import scallion.syntactic._

import scala.Option
import scala.runtime.Nothing$

// The parser for Amy
object Parser extends Pipeline[Iterator[Token], Program]
  with Syntaxes[Token, TokenKind] with Debug[Token, TokenKind]
  with Operators {

  import Implicits._

  override def getKind(token: Token): TokenKind = TokenKind.of(token)

  val eof: Syntax[Token] = elem(EOFKind)

  def op(string: String): Syntax[String] = accept(OperatorKind(string)) { case OperatorToken(name) => name }

  def opPos(string: String): Syntax[(String, Position)] = accept(OperatorKind(string)) {
    case op@OperatorToken(value) => (value, op.position)
  }

  def kw(string: String): Syntax[Token] = elem(KeywordKind(string))

  implicit def delimiter(string: String): Syntax[Token] = elem(DelimiterKind(string))

  // An entire program (the starting rule for any Amy file).
  lazy val program: Syntax[Program] = many1(many1(module) ~<~ eof).map(ms => Program(ms.flatten.toList).setPos(ms.head.head))

  // A module (i.e., a collection of definitions and an initializer expression)
  lazy val module: Syntax[ModuleDef] = (kw("object") ~ identifier ~ "{" ~ many(definition) ~ opt(expr) ~ "}").map {
    case obj ~ id ~ _ ~ defs ~ body ~ _ => ModuleDef(id, defs.toList, body).setPos(obj)
  }

  // An identifier.
  val identifier: Syntax[String] = accept(IdentifierKind) {
    case IdentifierToken(name) => name
  }


  lazy val slicing: Syntax[(Expr, Expr)] = recursive {
    delimiter("[").skip ~ simpleExpr ~ delimiter(":").skip ~ simpleExpr ~ delimiter("]").skip map {
      case expr0 ~ expr1 => (expr0, expr1)
    }
  }
  // An identifier along with its position.
  val identifierPos: Syntax[(String, Position)] = accept(IdentifierKind) {
    case id@IdentifierToken(name) => (name, id.position)
  }

  lazy val funDef: Syntax[ClassOrFunDef] = kw("def") ~ identifier ~ "(".skip ~
    parameters ~ ")".skip ~ ":".skip ~ typeTree ~ "=".skip ~
    "{".skip ~ expr ~ "}".skip map {
    case funDef ~ funName ~ params ~ funType ~ funBody =>
      FunDef(funName, params, funType, funBody).setPos(funDef)
  }

  lazy val abstrClass: Syntax[ClassOrFunDef] = kw("abstract") ~ kw("class").skip ~ identifier map {
    case abs ~ className => AbstractClassDef(className).setPos(abs)
  }

  lazy val caseClass: Syntax[ClassOrFunDef] = kw("case") ~ kw("class").skip ~ identifier ~
    "(".skip ~ parameters ~ ")".skip ~ kw("extends").skip ~ identifier map {
    case cas ~ className ~ funParams ~ parentClass =>
      CaseClassDef(className, funParams.map(_.tt), parentClass).setPos(cas)
  }

  // A definition within a module.
  lazy val definition: Syntax[ClassOrFunDef] = funDef | abstrClass | caseClass

  // A list of parameter definitions. Empty parameter list is not Unit !
  lazy val parameters: Syntax[List[ParamDef]] = repsep(nonEmptyParam, ",").map(_.toList)


  // A parameter definition, i.e., an identifier along with the expected type.
  lazy val nonEmptyParam: Syntax[ParamDef] = identifierPos ~ ":".skip ~ typeTree map {
    case (id, pos) ~ paramType => ParamDef(id, paramType).setPos(pos)
  }

  // A type expression.
  lazy val typeTree: Syntax[TypeTree] = primitiveType | identifierType

  // A built-in type (such as `Int`).
  val primitiveType: Syntax[TypeTree] = accept(PrimTypeKind) {
    case tk@PrimTypeToken(name) => TypeTree(name match {
      case "Unit" => UnitType
      case "Boolean" => BooleanType
      case "Int" => IntType
      case "String" => StringType
      case "Char" => CharType
      case _ => throw new java.lang.Error("Unexpected primitive type name: " + name)
    }).setPos(tk)
  }

  // A user-defined type (such as `List`).
  lazy val identifierType: Syntax[TypeTree] = identifierPos ~ opt(".".skip ~ identifier) map {
    case (modName, pos) ~ Some(ident) => TypeTree(ClassType(QualifiedName(Some(modName), ident))).setPos(pos)
    case (ident, pos) ~ None => TypeTree(ClassType(QualifiedName(None, ident))).setPos(pos)
  }


  // A pattern as part of a mach case.
  lazy val pattern: Syntax[Pattern] = recursive {
    literalPattern | wildPattern | aggregatePattern | unitPattern
  }


  lazy val unitPattern: Syntax[Pattern] = "(" ~ ")" map {
    case op ~ _ => LiteralPattern(UnitLiteral()).setPos(op)
  }

  lazy val literalPattern: Syntax[Pattern] = literal map {
    case lit => LiteralPattern(lit).setPos(lit)
  }

  lazy val wildPattern: Syntax[Pattern] = kw("_") map {
    case wild => WildcardPattern().setPos(wild)
  }

  lazy val patterns: Syntax[Seq[Pattern]] = recursive {
    repsep(pattern, ",")
  }

  lazy val aggregatePattern: Syntax[Pattern] = recursive {
    identifierPos ~ opt(opt("." ~ identifier) ~ "(" ~ patterns ~ ")") map {
      case (ident, pos) ~ None => IdPattern(ident).setPos(pos)
      case (ident, pos) ~ Some(None ~ _ ~ patts ~ _) => CaseClassPattern(QualifiedName(None, ident), patts.toList).setPos(pos)
      case (mod, pos) ~ Some(Some(_ ~ name) ~ _ ~ patts ~ _) => CaseClassPattern(QualifiedName(Some(mod), name), patts.toList).setPos(pos)
    }
  }

  // A literal expression.
  lazy val literal: Syntax[Literal[_]] = accept(LiteralKind) {
    case i@IntLitToken(value) => IntLiteral(value).setPos(i)
    case b@BoolLitToken(value) => BooleanLiteral(value).setPos(b)
    case s@StringLitToken(value) => StringLiteral(value).setPos(s)
    case c@CharLitToken(value) => CharLiteral(value).setPos(c)
  }

  lazy val expr: Syntax[Expr] = recursive {
    (exprNoVal | valExpr)
  }

  def foldMatchSeq(first: Expr, seq: Seq[Seq[MatchCase]]): Match = {
    seq.tail.foldLeft(Match(first, seq.head.toList))(
      (prevMatch, uni) => Match(prevMatch, uni.toList))
  }

  lazy val exprNoVal: Syntax[Expr] = recursive {
    valAssign ~ opt(";".skip ~ expr) map {
      case first ~ Some(next) => Sequence(first, next).setPos(first)
      case first ~ None => first
    }
  }

  lazy val valAssign: Syntax[Expr] = recursive {
    (binOp | conditionalExpr) ~ opt(many1(uniPatMatchExpr)) map {
      case first ~ Some(seq) => foldMatchSeq(first, seq).setPos(first)
      case first ~ None => first
    }
  }

  lazy val valExpr: Syntax[Expr] = kw("val") ~ identifier ~ ":".skip ~ typeTree ~ "=".skip ~ valAssign ~ ";".skip ~ expr map {
    case keyword ~ valName ~ valType ~ valValue ~ next => Let(ParamDef(valName, valType), valValue, next).setPos(keyword)
  }


  lazy val errorExpr: Syntax[Expr] = recursive {
    kw("error") ~ "(".skip ~ expr ~ ")".skip map { case errWord ~ exp => Error(exp).setPos(errWord) }
  }



  lazy val simpleExpr: Syntax[Expr] = recursive {
    errorExpr | maybeSlicing
  }

  lazy val maybeSlicing: Syntax[Expr] = recursive {
    (literal.up[Expr] | variableOrCall | ("(".skip ~ expr.opt ~ ")".skip map {
      case Some(e) => e
      case None => UnitLiteral()
    })) ~ many("[".skip ~ expr.opt ~ ":".skip ~ expr.opt ~ (":".skip ~ expr.opt).opt ~ "]".skip) map {
      case exp ~ slices =>
        //Shortcut: removes empty slicing
        val filteredSlices =
          slices.filter { case start ~ end ~ step =>
            start.isDefined || end.isDefined || (step.isDefined && step.get.isDefined) }

        if (filteredSlices.length == 0) exp
        else {
          val (start0 ~ end0 ~ step0) = filteredSlices.head

          def parseStepOption(step: Option[Option[Expr]]): Option[Expr] = step match {
            case Some(o) => o
            case None => None
          }


          filteredSlices.tail.foldLeft(Slice(exp, start0, end0, parseStepOption(step0))) {
            case (acc, currStart ~ currEnd ~ currStep) => Slice(acc, currStart, currEnd, parseStepOption(currStep))
          }
        }

    }
  }
  //if(cond) {} else {}
  lazy val conditionalExpr: Syntax[Expr] = kw("if") ~ "(".skip ~ expr ~ ")".skip ~
    "{".skip ~ expr ~ "}".skip ~ kw("else").skip ~ "{".skip ~ expr ~ "}".skip map {
    case keyword ~ cond ~ ifExpr ~ elseExpr => Ite(cond, ifExpr, elseExpr).setPos(keyword)
  }

  lazy val patMatchCase: Syntax[MatchCase] = kw("case") ~ pattern ~ "=>".skip ~ expr map {
    case keyword ~ p ~ e => MatchCase(p, e).setPos(keyword)
  }

  lazy val uniPatMatchExpr: Syntax[Seq[MatchCase]] = kw("match").skip ~ "{".skip ~ many1(patMatchCase) ~ "}".skip

  // (pattern not included, only simple expr allowed)
  //lazy val patMatchExpr: Syntax[Seq[Seq[MatchCase]]] = many1(uniPatMatchExpr)

  lazy val variableOrCall: Syntax[Expr] = identifierPos ~ opt(opt(".".skip ~ identifier) ~
    "(".skip ~ repsep(expr, ",") ~ ")".skip) map {
    case (mod, pos) ~ Some(Some(funName) ~ paramList) => Call(QualifiedName(Some(mod), funName), paramList.toList).setPos(pos)
    case (ident, pos) ~ Some(None ~ paramList) => Call(QualifiedName(None, ident), paramList.toList).setPos(pos)
    case (ident, pos) ~ None => Variable(ident).setPos(pos)
  }


  lazy val unaryOpLiteral: Syntax[(String, Position)] = opPos("-") | opPos("!")


  lazy val unaryOperand: Syntax[Expr] = simpleExpr
  //Position is minus sign's position
  lazy val unaryExpr: Syntax[Expr] = unaryOpLiteral ~ unaryOperand map {
    case ("!", pos) ~ exp => Not(exp).setPos(pos)
    case ("-", pos) ~ exp => Neg(exp).setPos(pos)
  }

  //Contains expressions with only unary or no operators
  lazy val operable: Syntax[Expr] = recursive {
    unaryOperand | unaryExpr
  }

  lazy val binOp: Syntax[Expr] = operators(operable)(
    op("*") | op("/") | op("%") is LeftAssociative,
    op("+") | op("-") | op("++") is LeftAssociative,
    op("<=") | op("<") is LeftAssociative,
    op("==") is LeftAssociative,
    op("&&") is LeftAssociative,
    op("||") is LeftAssociative,

  ) {
    case (lhs, "%", rhs) => Mod(lhs, rhs).setPos(lhs)
    case (lhs, "/", rhs) => Div(lhs, rhs).setPos(lhs)
    case (lhs, "*", rhs) => Times(lhs, rhs).setPos(lhs)
    case (lhs, "++", rhs) => Concat(lhs, rhs).setPos(lhs)
    case (lhs, "-", rhs) => Minus(lhs, rhs).setPos(lhs)
    case (lhs, "+", rhs) => Plus(lhs, rhs).setPos(lhs)
    case (lhs, "<=", rhs) => LessEquals(lhs, rhs).setPos(lhs)
    case (lhs, "<", rhs) => LessThan(lhs, rhs).setPos(lhs)
    case (lhs, "==", rhs) => Equals(lhs, rhs).setPos(lhs)
    case (lhs, "&&", rhs) => And(lhs, rhs).setPos(lhs)
    case (lhs, "||", rhs) => Or(lhs, rhs).setPos(lhs)
  }


  // Ensures the grammar is in LL(1), otherwise prints some counterexamples
  lazy val checkLL1: Boolean = {
    if (program.isLL1) {
      true
    } else {
      debug(program)
      false
    }
  }

  override def run(ctx: Context)(tokens: Iterator[Token]): Program = {
    import ctx.reporter._
    if (!checkLL1) {
      ctx.reporter.fatal("Program grammar is not LL1!")
    }

    program(tokens) match {
      case Parsed(result, rest) => result
      case UnexpectedEnd(rest) => fatal("Unexpected end of input.")
      case UnexpectedToken(token, rest) => fatal("Unexpected token: " + token + ", possible kinds: " + rest.first.map(_.toString).mkString(", "))
    }
  }
}
