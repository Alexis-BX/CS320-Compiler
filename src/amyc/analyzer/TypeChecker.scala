package amyc
package analyzer

import utils._
import ast.SymbolicTreeModule._
import ast.Identifier

// The type checker for Amy
// Takes a symbolic program and rejects it if it does not follow the Amy typing rules.
object TypeChecker extends Pipeline[(Program, SymbolTable), (Program, SymbolTable)] {


  def run(ctx: Context)(v: (Program, SymbolTable)): (Program, SymbolTable) = {
    import ctx.reporter._

    val (program, table) = v


    case class Constraint(found: Type, expected: Type, pos: Position)
    object Constraint {
      def apply(found: Type, expected: Type, pos: Positioned): Constraint =
        Constraint(found, expected, pos.position)
    }

    // Represents a type variable.
    // It extends Type, but it is meant only for internal type checker use,
    //  since no Amy value can have such type.
    case class TypeVariable private(id: Int) extends Type
    object TypeVariable {
      private val c = new UniqueCounter[Unit]

      def fresh(): TypeVariable = TypeVariable(c.next(()))
    }



    // Generates typing constraints for an expression `e` with a given expected type.
    // The environment `env` contains all currently available bindings (you will have to
    //  extend these, e.g., to account for local variables).
    // Returns a list of constraints among types. These will later be solved via unification.
    def genConstraints(e: Expr, expected: Type)(implicit env: Map[Identifier, Type]): List[Constraint] = {

      // This helper returns a list of a single constraint recording the type
      //  that we found (or generated) for the current expression `e`
      def topLevelConstraint(found: Type): List[Constraint] =
        List(Constraint(found, expected, e))

      def handleBinOp(retType: Type, exprType: Type, lhs: Expr, rhs: Expr): List[Constraint] =
        topLevelConstraint(retType) :::
          genConstraints(lhs, exprType) :::
          genConstraints(rhs, exprType)


      def simpleBinOp(expected: Type, lhs: Expr, rhs: Expr): List[Constraint] =
        handleBinOp(expected, expected, lhs, rhs)

      def unaryOp(expected: Type, operand: Expr): List[Constraint] =
        topLevelConstraint(expected) ::: genConstraints(operand, expected)


      e match {
        // LITERALS
        case IntLiteral(_) =>
          topLevelConstraint(IntType)

        case UnitLiteral() =>
          topLevelConstraint(UnitType)

        case BooleanLiteral(_) =>
          topLevelConstraint(BooleanType)

        case StringLiteral(_) =>
          topLevelConstraint(StringType)

        case CharLiteral(_) =>
          topLevelConstraint(CharType)

        // BINARY OPERATORS

        case Plus(lhs, rhs) => simpleBinOp(IntType, lhs, rhs)
        case Minus(lhs, rhs) => simpleBinOp(IntType, lhs, rhs)
        case Times(lhs, rhs) => simpleBinOp(IntType, lhs, rhs)
        case Div(lhs, rhs) => simpleBinOp(IntType, lhs, rhs)
        case Mod(lhs, rhs) => simpleBinOp(IntType, lhs, rhs)
        case LessThan(lhs, rhs) => handleBinOp(BooleanType, IntType, lhs, rhs)
        case LessEquals(lhs, rhs) => handleBinOp(BooleanType, IntType, lhs, rhs)
        case And(lhs, rhs) => simpleBinOp(BooleanType, lhs, rhs)
        case Or(lhs, rhs) => simpleBinOp(BooleanType, lhs, rhs)
        case Concat(lhs, rhs) => simpleBinOp(StringType, lhs, rhs)


        case Equals(lhs, rhs) =>
          val operandsType = TypeVariable.fresh()
          val recConstraints = genConstraints(lhs, operandsType) ::: genConstraints(rhs, operandsType)
          topLevelConstraint(BooleanType) ::: recConstraints

        case Slice(e, start, end, step) =>

          def constrFromOpt(option: Option[Expr]) = if (option.isDefined) genConstraints(option.get, IntType) else List()

          topLevelConstraint(StringType) :::
            genConstraints(e, StringType) :::
            constrFromOpt(start) :::
            constrFromOpt(end) :::
            constrFromOpt(step)

        // UNARY OPERATORS

        case Not(e) => unaryOp(BooleanType, e)
        case Neg(e) => unaryOp(IntType, e)

        // PATTERN MATCHING EXPRESSION

        case Match(scrut, cases) =>
          // Returns additional constraints from within the pattern with all bindings
          // from identifiers to types for names bound in the pattern.
          // (This is analogous to `transformPattern` in NameAnalyzer.)
          def handlePattern(pat: Pattern, scrutExpected: Type):
          (List[Constraint], Map[Identifier, Type]) = {
            val paramsConstraints = pat match {
              case WildcardPattern() => (List[Constraint](), Map[Identifier, Type]())
              case IdPattern(name) => {
                val idType = TypeVariable.fresh()
                //Cannot use topLevelConstraint, otherwise constrain on type of FULL match expr (i.e. return type of match exprs)
                val idConstr = Constraint(idType, scrutExpected, pat)
                (List(idConstr), Map(name -> idType))

              }
              case LiteralPattern(lit) => {
                val literalType = lit match {
                  case IntLiteral(_) => IntType
                  case CharLiteral(_) => CharType
                  case BooleanLiteral(_) => BooleanType
                  case StringLiteral(_) => StringType
                  case UnitLiteral() => UnitType
                }
                Constraint(scrutExpected, literalType, pat)
                (List(Constraint(scrutExpected, literalType, pat)),
                  Map[Identifier, Type]())
              }
              case CaseClassPattern(constr, args) => {

                val constrSig = table.getConstructor(constr).get //Know that in table, caught by name Analyzer
                val scrutConstraint = Constraint(constrSig.retType, scrutExpected, pat)
                //bind params to formal types
                val (constrRec, mapRec) = (args zip constrSig.argTypes).foldLeft((List[Constraint](), Map[Identifier, Type]())) {
                  case ((ls, mp), (arg, formalType)) =>
                    val (newConstraints, identToTypes) = handlePattern(arg, formalType)

                    (ls ::: newConstraints, mp ++ identToTypes)
                }

                (scrutConstraint :: constrRec, mapRec)
              }
            }
            paramsConstraints
          }

          def handleCase(cse: MatchCase, scrutExpected: Type): List[Constraint] = {
            val (patConstraints, moreEnv) = handlePattern(cse.pat, scrutExpected)
            val bodyConstraints = genConstraints(cse.expr, expected)(env ++ moreEnv)
            bodyConstraints ::: patConstraints
          }

          //Generate new type for scrutinee, forces patterns to have same return type (?)
          val st = TypeVariable.fresh()
          genConstraints(scrut, st) ++ cases.flatMap(cse => handleCase(cse, st))


        case Variable(name) => {
          // env will contain entry for name, otherwise would have been caught at name analysis
          topLevelConstraint(env(name))


        }

        // Function/constructor call
        case Call(qname, args) => {
          // Again, we know that function or constructor exists
          val callSig = table.getFunction(qname).getOrElse(table.getConstructor(qname).get)
          val argsContraints = args.zip(callSig.argTypes).flatMap {
            case (argExpr, formalTpe) =>

              val callType = TypeVariable.fresh()

              //Gen constraints for argument, bind it to format expected type
              Constraint(callType, formalTpe, argExpr) :: genConstraints(argExpr, callType)

          }
          topLevelConstraint(callSig.retType) ::: argsContraints
        }
        case Sequence(e1, e2) => {
          //No condition on type for expr e1, but e2 should have expected type of whole expr
          val wholeType = TypeVariable.fresh()
          topLevelConstraint(wholeType) :::
            genConstraints(e1, TypeVariable.fresh()) :::
            genConstraints(e2, wholeType)
        }
        case Let(df, value, body) => {

          val wholeType = TypeVariable.fresh()
          topLevelConstraint(wholeType) :::
            genConstraints(value, df.tt.tpe) :::
            genConstraints(body, wholeType)(env + (df.name -> df.tt.tpe))
        }
        case Ite(cond, thenn, elze) => {
          val wholeType = TypeVariable.fresh()
          topLevelConstraint(wholeType) :::
            genConstraints(cond, BooleanType) :::
            genConstraints(thenn, wholeType) :::
            genConstraints(elze, wholeType)

        }
        case Error(msg) => genConstraints(msg, StringType)
        case _ => fatal("PARSER, LEXER OR NAME ANALYZER FAILED IN THEIR DUTY !!!", e)
      }
    }


    // Given a list of constraints `constraints`, replace every occurence of type variable
    //  with id `from` by type `to`.
    def subst_*(constraints: List[Constraint], from: Int, to: Type): List[Constraint] = {
      // Do a single substitution.
      def subst(tpe: Type, from: Int, to: Type): Type = {
        tpe match {
          case TypeVariable(`from`) => to
          case other => other
        }
      }

      constraints map { case Constraint(found, expected, pos) =>
        Constraint(subst(found, from, to), subst(expected, from, to), pos)
      }
    }

    // Solve the given set of typing constraints and
    //  call `typeError` if they are not satisfiable.
    // We consider a set of constraints to be satisfiable exactly if they unify.
    def solveConstraints(constraints: List[Constraint]): Unit = {
      constraints match {
        case Nil => ()
        case (head@Constraint(found, expected, pos)) :: more =>
          // HINT: You can use the `subst_*` helper above to replace a type variable
          //       by another type in your current set of constraints.

          //Delete useless
          if (found == expected) solveConstraints(more)
          else {
            found match {
              case TypeVariable(id) => {
                solveConstraints(subst_*(more, id, expected))
              }
              case _ => {
                expected match {
                  case TypeVariable(id) => {
                    solveConstraints(subst_*(more, id, found))
                  }
                  case _ => {
                    error(s"Type error: expected $expected, found $found", pos)
                    solveConstraints(more)
                  }
                }
              }
            }
          }
      }
    }

    // Putting it all together to type-check each module's functions and main expression.
    program.modules.foreach { mod =>

      // Put function parameters to the symbol table, then typecheck them against the return type
      mod.defs.collect { case FunDef(_, params, retType, body) =>
        val env = params.map { case ParamDef(name, tt) => name -> tt.tpe }.toMap
        solveConstraints(genConstraints(body, retType.tpe)(env))
      }

      // Type-check expression if present. We allow the result to be of an arbitrary type by
      // passing a fresh (and therefore unconstrained) type variable as the expected type.
      val tv = TypeVariable.fresh()
      mod.optExpr.foreach(e => solveConstraints(genConstraints(e, tv)(Map())))
    }

    v

  }
}
