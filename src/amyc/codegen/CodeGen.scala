package amyc
package codegen

import analyzer._
import ast.Identifier
import ast.SymbolicTreeModule.{And => AmyAnd, Call => AmyCall, Div => AmyDiv, Or => AmyOr, _}
import utils.{Context, Pipeline}
import wasm.{Instructions, _}
import Utils._
import amyc.wasm.Instructions._

// Generates WebAssembly code for an Amy program
object CodeGen extends Pipeline[(Program, SymbolTable), Module] {
  def run(ctx: Context)(v: (Program, SymbolTable)): Module = {
    val (program, table) = v

    // Generate code for an Amy module
    def cgModule(moduleDef: ModuleDef): List[Function] = {
      val ModuleDef(name, defs, optExpr) = moduleDef
      // Generate code for all functions
      defs.collect { case fd: FunDef if !builtInFunctions(fullName(name, fd.name)) =>
        cgFunction(fd, name, false)
      } ++
        // Generate code for the "main" function, which contains the module expression
        optExpr.toList.map { expr =>
          val mainFd = FunDef(Identifier.fresh("main"), Nil, TypeTree(IntType), expr)
          cgFunction(mainFd, name, true)
        }
    }


    // Generate code for a function in module 'owner'
    def cgFunction(fd: FunDef, owner: Identifier, isMain: Boolean): Function = {
      // Note: We create the wasm function name from a combination of
      // module and function name, since we put everything in the same wasm module.
      val name = fullName(owner, fd.name)
      Function(name, fd.params.size, isMain) { lh =>
        val locals = fd.paramNames.zipWithIndex.toMap
        val body = cgExpr(fd.body)(locals, lh)
        if (isMain) {
          body <:> Drop // Main functions do not return a value,
          // so we need to drop the value generated by their body
        } else {
          body
        }
      }
    }

    // Generate code for an expression expr.
    // Additional arguments are a mapping from identifiers (parameters and variables) to
    // their index in the wasm local variables, and a LocalsHandler which will generate
    // fresh local slots as required.
    def cgExpr(expr: Expr)(implicit locals: Map[Identifier, Int], lh: LocalsHandler): Code = {
      // Generates an error message and crashes program
      def error(message: String, positionExpr: Option[Expr] = None): Code = {
        (if (positionExpr.isDefined) mkString(s"Error: $message at ${positionExpr.get.position.toString}")
        else mkString(s"Error: $message")) <:>
          Call("Std_printString") <:>
          Unreachable

      }

      expr match {

        case Variable(name) => GetLocal(locals(name))

        case IntLiteral(value) => Const(value)
        case BooleanLiteral(value) => Const(if (value) 1 else 0)
        case StringLiteral(value) => mkString(value)
        case CharLiteral(value) => Const(value)
        case UnitLiteral() => Const(0)

        case Plus(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Add

        case Minus(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Sub
        case Times(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Mul
        case AmyDiv(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Div
        case Mod(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Rem
        case LessThan(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Lt_s
        case LessEquals(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Le_s
        case AmyAnd(lhs, rhs) =>
          cgExpr(lhs) <:>
            If_i32 <:>
            cgExpr(rhs) <:>
            Else <:>
            Const(0) <:>
            End

        case AmyOr(lhs, rhs) =>
          cgExpr(lhs) <:>
            If_i32 <:>
            Const(1) <:>
            Else <:>
            cgExpr(rhs) <:>
            End
        case Equals(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Eq
        case Concat(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Call("String_concat")

        case Not(e) => cgExpr(e) <:> Eqz
        case Neg(e) => Const(0) <:> cgExpr(e) <:> Sub

        // Function or constructor call
        case AmyCall(qname, args) => {
          table.getFunction(qname) match {
            case Some(funSig) =>

              args.flatMap(cgExpr(_).instructions) <:> Call(fullName(funSig.owner, qname))
            case None =>
              val constrSig = table.getConstructor(qname).get

              // #fields + 1 for constructor
              val len = constrSig.argTypes.length + 1
              val oldBoundary = lh.getFreshLocal()

              // Get old boundary, save it to local, and update it
              GetGlobal(memoryBoundary) <:> SetLocal(oldBoundary) <:> GetGlobal(memoryBoundary) <:>
                Const(len * 4) <:> Add <:> SetGlobal(memoryBoundary) <:>
                // Save constructor index
                GetLocal(oldBoundary) <:> Const(constrSig.index) <:> Store <:>
                // Evaluate parameters, leave them on stack, then save in memory
                args.zipWithIndex.flatMap { case (e, field) =>
                  (GetLocal(oldBoundary) <:> adtField(field) <:> cgExpr(e) <:> Store).instructions
                } <:> GetLocal(oldBoundary)

          }
        }

        case Slice(e, start, end, step) =>
          // Only need to compute string ref, start, end and step. Everything else is delegated

          val startC: Code = if (start.isDefined) cgExpr(start.get) else Const(0)
          val stepC: Code = if (step.isDefined) cgExpr(step.get) else Const(1)

          val argsPreparation: Code = (if (end.isDefined) {
            cgExpr(e) <:>
              startC <:>
              cgExpr(end.get)
          } else {
            val scrutRef = lh.getFreshLocal()
            cgExpr(e) <:>
              SetLocal(scrutRef) <:>
              GetLocal(scrutRef) <:>
              startC <:>
              GetLocal(scrutRef) <:>
              Call("S_length")
          }) <:> stepC <:>
            Const(if(start.isDefined) 0 else 1) <:>
            Const(if(end.isDefined) 0 else 1)


          argsPreparation <:> Call("S_Slice")

        case Sequence(e1, e2) =>
          cgExpr(e1) <:> Drop <:> cgExpr(e2)

        // Local variable definition
        case Let(df, value, body) =>
          val varId = lh.getFreshLocal()
          cgExpr(value) <:> SetLocal(varId) <:> cgExpr(body)(locals + (df.name -> varId), lh)


        case Ite(cond, thenn, elze) =>

          cgExpr(cond) <:> If_i32 <:> cgExpr(thenn) <:> Else <:> cgExpr(elze) <:> End

        case Match(scrut, cases) => {
          // Assume that value to bind is on stack
          def matchAndBind(p: Pattern): (Code, Map[Identifier, Int]) = {
            p match {
              case WildcardPattern() => (Drop <:> Const(1), Map.empty)
              case IdPattern(name) =>
                val id = lh.getFreshLocal()
                (SetLocal(id) <:> Const(1), Map(name -> id))
              case LiteralPattern(lit) =>
                (cgExpr(lit) <:> Eq, Map.empty)

              case CaseClassPattern(constr, args) => {
                val constrSig = table.getConstructor(constr).get
                val scrutLocal = lh.getFreshLocal()
                val (argCode, argMap): (Code, Map[Identifier, Int]) =
                  if (args.isEmpty)
                    (Const(1), Map.empty[Identifier, Int])
                  else {
                    val (code, map) = args.zipWithIndex.map { case (pp, field) =>
                      val (pCode, pMap) = matchAndBind(pp)
                      (GetLocal(scrutLocal) <:>
                        adtField(field) <:>
                        Load <:>
                        pCode <:> And, pMap)
                    }.reduce((c0, c1) =>
                      (c0._1 <:> c1._1,
                        c0._2 ++ c1._2
                      ))
                    (Const(1) <:>
                      code, map)
                  }

                (SetLocal(scrutLocal) <:>
                  GetLocal(scrutLocal) <:>
                  Load <:>
                  Const(constrSig.index) <:>
                  Eq <:> // Constructor no more on stack
                  If_i32 <:>
                  argCode <:> //Generate code for subsequent patterns 'And' all to get final result
                  Else <:>
                  Const(0) <:>
                  End, argMap)
              }
            }
          }

          val scrutID = lh.getFreshLocal()

          cgExpr(scrut) <:>
            SetLocal(scrutID) <:>
            cases.flatMap(c => {
              val (newCode, newLocals) = matchAndBind(c.pat)
              (GetLocal(scrutID) <:>
                newCode <:>
                If_i32 <:>
                cgExpr(c.expr)(locals ++ newLocals, lh) <:>
                Else).instructions
            }) <:>
            error("match error", Some(scrut)) <:>
            cases.map(_ => i2c(End)).reduce((c0, c1) => c0 <:> c1)


        }

        // Since an arbitrary message can be passed to error, did not use the
        case Error(msg) => mkString("Error: ") <:> cgExpr(msg) <:>
          Call("String_concat") <:> Call("Std_printString") <:> Unreachable

        case _ => error("Code generator met unknown expression")
      }
    }


    Module(
      program.modules.last.name.name,
      defaultImports,
      globalsNo,
      wasmFunctions ++ (program.modules flatMap cgModule)
    )

  }
}
