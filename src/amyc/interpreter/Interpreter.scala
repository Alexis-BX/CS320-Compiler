package amyc
package interpreter

import amyc.analyzer.SymbolTable
import amyc.ast.Identifier
import amyc.ast.SymbolicTreeModule._
import amyc.utils._

// An interpreter for Amy programs, implemented in Scala
object Interpreter extends Pipeline[(Program, SymbolTable), Unit] {

  def run(ctx: Context)(v: (Program, SymbolTable)): Unit = {
    val (program, table) = v

    // These built-in functions do not have an Amy implementation in the program,
    // instead their implementation is encoded in this map
    val builtIns: Map[(String, String), (List[Value]) => Value] = Map(
      ("Std", "printInt") -> { args => println(args.head.asInt); UnitValue },
      ("Std", "printString") -> { args => println(args.head.asString); UnitValue },
      ("Std", "printChar") -> { args => println(args.head.asString); UnitValue },
      ("Std", "readString") -> { args => StringValue(scala.io.StdIn.readLine()) },
      ("Std", "readInt") -> { args =>
        val input = scala.io.StdIn.readLine()
        try {
          IntValue(input.toInt)
        } catch {
          case ne: NumberFormatException =>
            ctx.reporter.fatal(s"""Could not parse "$input" to Int""")
        }
      },
      ("Std", "intToString") -> { args => StringValue(args.head.asInt.toString) },
      ("Std", "digitToString") -> { args => StringValue(args.head.asInt.toString) },
      ("Std", "charToString") -> { args => ??? },
      ("Std", "charToInt") -> { args => ??? },
      ("Std", "IntToChar") -> {args => ???}
    )


    // Utility functions to interface with the symbol table.
    def isConstructor(name: Identifier) = table.getConstructor(name).isDefined

    def findFunctionOwner(functionName: Identifier) = table.getFunction(functionName).get.owner.name

    def findFunction(owner: String, name: String) = {
      program.modules.find(_.name.name == owner).get.defs.collectFirst {
        case fd@FunDef(fn, _, _, _) if fn.name == name => fd
      }.get
    }

    // Interprets a function, using evaluations for local variables contained in 'locals'
    // TODO: Complete all missing cases. Look at the given ones for guidance.
    def interpret(expr: Expr)(implicit locals: Map[Identifier, Value]): Value = {
      expr match {
        case Variable(name) =>
          locals(name)

        case IntLiteral(i) =>
          IntValue(i)
        case BooleanLiteral(b) =>
          BooleanValue(b)
        case StringLiteral(s) =>
          StringValue(s)
        case UnitLiteral() =>
          UnitValue
        case Plus(lhs, rhs) =>
          IntValue(interpret(lhs).asInt + interpret(rhs).asInt)
        case Minus(lhs, rhs) =>
          IntValue(interpret(lhs).asInt - interpret(rhs).asInt)
        case Times(lhs, rhs) =>
          IntValue(interpret(lhs).asInt * interpret(rhs).asInt)
        case Div(lhs, rhs) => BooleanLiteral
          val l = interpret(lhs)
          val r = interpret(rhs)
          if (r.asInt != 0)
            IntValue(l.asInt / r.asInt)
          else
            ctx.reporter.fatal("Division by 0")
        case Mod(lhs, rhs) =>
          IntValue(interpret(lhs).asInt % interpret(rhs).asInt)
        case LessThan(lhs, rhs) =>
          BooleanValue(interpret(lhs).asInt < interpret(rhs).asInt)
        case LessEquals(lhs, rhs) =>
          BooleanValue(interpret(lhs).asInt <= interpret(rhs).asInt)
        case And(lhs, rhs) =>
          BooleanValue(interpret(lhs).asBoolean && interpret(rhs).asBoolean)
        case Or(lhs, rhs) =>
          BooleanValue(interpret(lhs).asBoolean || interpret(rhs).asBoolean)
        case Equals(lhs, rhs) => {
          val v1 = interpret(lhs)
          val v2 = interpret(rhs)

          val bRes = v1 match {
            case StringValue(s) => BooleanValue(v1.eq(v2))
            case IntValue(i) => BooleanValue(i == v2.asInt)
            //Since UnitValue is a (unique) object, can check by ref
            case UnitValue => BooleanValue(v1.eq(v2))
            case BooleanValue(b) => BooleanValue(b == v2.asBoolean)
            //Amy ref compiler compares Case classes by ref
            case CaseClassValue(_, _) => BooleanValue(v1.eq(v2))
          }
          bRes
        }
        case Concat(lhs, rhs) =>
          StringValue(interpret(lhs).asString ++ interpret((rhs)).asString)
        case Not(e) =>
          BooleanValue(!interpret((e)).asBoolean)
        case Neg(e) =>
          IntValue(-interpret(e).asInt)
        case Call(qname, args) => {
          val nArgs = args.map(v => interpret(v))
          if (isConstructor(qname)) {
            CaseClassValue(qname, nArgs)
          } else {
            val owner = findFunctionOwner(qname)
            val name = qname.name
            if (builtIns.contains(owner, name)) {
              builtIns(owner, name)(nArgs)
            } else {
              val fDef = findFunction(owner, name)
              val interMap = fDef.params.zip(nArgs).map { case (param, value) => param.name -> value }.toMap
              val nLocals = locals ++ interMap

              interpret(fDef.body)(nLocals)

            }
          }
        }


        case Sequence(e1, e2) => {
          interpret(e1);
          interpret(e2);

        }
        case Let(df, value, body) => {
          val res = interpret(value)
          val nLocals = locals + (df.name -> res)
          interpret(body)(nLocals)
        }

        case Ite(cond, thenn, elze) => {
          if (interpret(cond).asBoolean) {
            interpret(thenn)
          } else {
            interpret(elze)
          }
        }
        case Match(scrut, cases) => {
          // Hint: We give you a skeleton to implement pattern matching
          //       and the main body of the implementation

          val evS = interpret(scrut)


          // Returns a list of pairs id -> value,
          // where id has been bound to value within the pattern.
          // Returns None when the pattern fails to match.
          // Note: Only works on well typed patterns (which have been ensured by the type checker).
          // v: Value of interpreted "input" pattern
          // pattern: in match pattern. Note: Pattern must be atomic, not further evaluable
          def matchesPattern(v: Value, pat: Pattern): Option[List[(Identifier, Value)]] = {
            ((v, pat): @unchecked) match {
              case (_, WildcardPattern()) =>
                Some(List())
              case (_, IdPattern(name)) =>
                Some(List(name -> v))
              case (IntValue(i1), LiteralPattern(IntLiteral(i2))) =>
                if (i1 == i2) Some(List()) else None
              case (BooleanValue(b1), LiteralPattern(BooleanLiteral(b2))) =>
                if (b1 == b2) Some(List()) else None
              case (StringValue(_), LiteralPattern(StringLiteral(_))) =>
                //Since Amy compares Strings by reference, literal always has diff value from input pattern
                None
              case (UnitValue, LiteralPattern(UnitLiteral())) =>
                Some(List())
              case (CharValue(c), LiteralPattern(CharLiteral(c2))) =>
                if (c == c2) Some(List()) else None
              case (CaseClassValue(con1, realArgs), CaseClassPattern(con2, formalArgs)) =>
                if (con1.name == con2.name) {
                  /**
                    * try to bind each Value (defined below) to a pattern (can be literal, another case class, and so on)
                    * Then flatMap in order to skip None and flatMap again to extract elems of lists into a single list
                    */
                  val iRes = formalArgs.zip(realArgs)
                  val res = formalArgs.zip(realArgs).flatMap { case (patt, pattValue) =>
                    matchesPattern(pattValue, patt)
                  }
                  //If all values could match with a pattern (None skiped by flatMap => if no match, smaller list)
                  if (res.length == iRes.length) {
                    Some(res.flatten)
                  } else {
                    None
                  }

                } else None
            }
          }

          // Main "loop" of the implementation: Go through every case,
          // check if the pattern matches, and if so return the evaluation of the case expression
          for {
            //For eatch MatchCase of cases
            MatchCase(pat, rhs) <- cases

            //Morelocals is a map containing at mose 1 binding (may be empty binding, in case matched value is unused)
            moreLocals <- matchesPattern(evS, pat)
          } {
            return interpret(rhs)(locals ++ moreLocals)
          }
          // No case matched: The program fails with a match error
          ctx.reporter.fatal(s"Match error: ${evS.toString}@${scrut.position}")

        }

        case Error(msg) =>
          ctx.reporter.fatal(s"Error from Amy program: ${interpret(msg).toString}@${msg.position}")

      }
    }

    // Body of the interpreter: Go through every module in order
    // and evaluate its expression if present
    for {
      m <- program.modules
      e <- m.optExpr
    } {
      interpret(e)(Map())
    }
  }

  // A class that represents a value computed by interpreting an expression
  abstract class Value {
    def asInt: Int = this.asInstanceOf[IntValue].i

    def asBoolean: Boolean = this.asInstanceOf[BooleanValue].b

    def asString: String = this.asInstanceOf[StringValue].s

    override def toString: String = this match {
      case IntValue(i) => i.toString
      case BooleanValue(b) => b.toString
      case StringValue(s) => s
      case CharValue(c) => c.toString
      case UnitValue => "()"
      case CaseClassValue(constructor, args) =>
        constructor.name + "(" + args.map(_.toString).mkString(", ") + ")"
    }
  }

  case class IntValue(i: Int) extends Value

  case class BooleanValue(b: Boolean) extends Value

  case class StringValue(s: String) extends Value

  case class CharValue(c: Char) extends Value

  case class CaseClassValue(constructor: Identifier, args: List[Value]) extends Value

  case object UnitValue extends Value

}
