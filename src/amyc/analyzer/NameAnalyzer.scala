package amyc
package analyzer

import utils._
import ast.{Identifier, NominalTreeModule => N, SymbolicTreeModule => S}

// Name analyzer for Amy
// Takes a nominal program (names are plain strings, qualified names are string pairs)
// and returns a symbolic program, where all names have been resolved to unique Identifiers.
// Rejects programs that violate the Amy naming rules.
// Also populates and returns the symbol table.
object NameAnalyzer extends Pipeline[N.Program, (S.Program, SymbolTable)] {
  def run(ctx: Context)(p: N.Program): (S.Program, SymbolTable) = {
    import ctx.reporter._

    // Step 0: Initialize symbol table
    val table = new SymbolTable

    // Step 1: Add modules to table 
    val modNames = p.modules.groupBy(_.name)
    modNames.foreach { case (name, modules) =>
      if (modules.size > 1) {
        fatal(s"Two modules named $name in program", modules.head.position)
      }
    }

    modNames.keys.toList foreach table.addModule


    // Helper method: will transform a nominal type 'tt' to a symbolic type,
    // given that we are within module 'inModule'.
    def transformType(tt: N.TypeTree, inModule: String): S.Type = {
      tt.tpe match {
        case N.IntType => S.IntType
        case N.BooleanType => S.BooleanType
        case N.StringType => S.StringType
        case N.UnitType => S.UnitType
        case N.CharType => S.CharType
        case N.ClassType(qn@N.QualifiedName(module, name)) =>
          //When qualifiedName, if module option specified, take this module, otherwise use (current) inModule module
          table.getType(module getOrElse inModule, name) match {
            case Some(symbol) =>
              S.ClassType(symbol)
            case None =>
              fatal(s"Could not find type $qn", tt)
          }
      }
    }

    // Step 2: Check name uniqueness of definitions in each module
    p.modules.foreach { case module =>
      module.defs.groupBy(_.name) foreach { case (name, definitions) =>
        if (definitions.length > 1) {
          fatal(s"Two definitions named $name in module ${module.name}", definitions.head.position)
        }
      }
    }



    // Step 3: Discover types and add them to symbol table
    p.modules.foreach { case module =>
      module.defs foreach { case classOrFunDef => classOrFunDef match {
        case N.AbstractClassDef(name) => table.addType(module.name, name);
        case _ => //Do nothing for functions and case classes
      }
      }
    }

    // Step 4: Discover type constructors, add them to table
    p.modules.foreach { case module =>
      module.defs.foreach(inModDef => inModDef match {
        case N.CaseClassDef(name, fields, parent) =>

          val parentIdentifier = table.getType(module.name, parent) match {
            case Some(id) => id
            case None => fatal(s"Parent class ${parent} not found", inModDef)
          }
          table.addConstructor(
            module.name,
            name,
            fields.map(transformType(_, module.name)),
            parentIdentifier
          )
        case _ => //Noting for abstract classes and function defitions
      })
    }

    // Step 5: Discover functions signatures, add them to table
    p.modules.foreach {
      case module =>
        module.defs.foreach(_ match {
          case N.FunDef(name, params, retType, body) =>
            val paramTypes = params.map { case p => transformType(p.tt, module.name) }
            table.addFunction(module.name, name, paramTypes, transformType(retType, module.name))
          case _ => //Do nothing for classes/types
        })
    }

    // Step 6: We now know all definitions in the program.
    //         Reconstruct modules and analyse function bodies/ expressions

    // This part is split into three transfrom functions,
    // for definitions, FunDefs, and expressions.
    // Keep in mind that we transform constructs of the NominalTreeModule 'N' to respective constructs of the SymbolicTreeModule 'S'.
    // transformFunDef is given as an example, as well as some code for the other ones

    def transformDef(df: N.ClassOrFunDef, module: String): S.ClassOrFunDef = {
      df match {
        case N.AbstractClassDef(name) =>
          transformAstractCaseClassDef(name, module)
        case N.CaseClassDef(name, _, _) =>
          transformCaseClassDef(name, module)
        case fd: N.FunDef =>
          transformFunDef(fd, module)
      }
      }.setPos(df)

    def transformCaseClassDef(name: N.Name, module: String): S.CaseClassDef = {
      val Some((constrID, sig)) = table.getConstructor(module, name)
      val newFields = sig.argTypes map (t => S.TypeTree(t))
      S.CaseClassDef(constrID, newFields, sig.parent)
    }


    def transformAstractCaseClassDef(strName: N.Name, module: String): S.AbstractClassDef = {
      val Some(identifierName) = table.getType(module, strName)
      S.AbstractClassDef(identifierName)
    }

    def transformFunDef(fd: N.FunDef, module: String): S.FunDef = {
      val N.FunDef(name, params, retType, body) = fd
      val Some((sym, sig)) = table.getFunction(module, name)

      params.groupBy(_.name).foreach { case (name, ps) =>
        if (ps.size > 1) {
          fatal(s"Two parameters named $name in function ${fd.name}", fd)
        }
      }

      val paramNames = params.map(_.name)

      val newParams = params zip sig.argTypes map { case (pd@N.ParamDef(name, tt), tpe) =>
        val s = Identifier.fresh(name)
        S.ParamDef(s, S.TypeTree(tpe).setPos(tt)).setPos(pd)
      }

      val paramsMap = paramNames.zip(newParams.map(_.name)).toMap

      S.FunDef(
        sym,
        newParams,
        S.TypeTree(sig.retType).setPos(retType),
        transformExpr(body)(module, (paramsMap, Map()))
      ).setPos(fd)
    }


    // This function takes as implicit a pair of two maps:
    // The first is a map from names of parameters to their unique identifiers,
    // the second is similar for local variables.
    // Make sure to update them correctly if needed given the scoping rules of Amy
    def transformExpr(expr: N.Expr)
                     (implicit module: String, names: (Map[String, Identifier], Map[String, Identifier])): S.Expr = {
      val (params, locals) = names

      def transformLiteral[T](l: N.Literal[T]) = (l match {
        case N.IntLiteral(value) => S.IntLiteral(value)
        case N.BooleanLiteral(value) => S.BooleanLiteral(value)
        case N.StringLiteral(value) => S.StringLiteral(value)
        case N.UnitLiteral() => S.UnitLiteral()
        case N.CharLiteral(c) => S.CharLiteral(c)
      }).setPos(l)

      val res = expr match {
        case N.Match(scrut, cases) =>
          // Returns a transformed pattern along with all bindings
          // from strings to unique identifiers for names bound in the pattern.
          // Also, calls 'fatal' if a new name violates the Amy naming rules.
          def transformPattern(pat: N.Pattern): (S.Pattern, List[(String, Identifier)]) = {
            pat match {
              case N.WildcardPattern() => (S.WildcardPattern(), List())
              case N.IdPattern(strName) => {
                //Emit warning
                table.getConstructor(module, strName) match {
                  case Some((_, sig)) =>
                    if(sig.argTypes.length == 0)
                      warning(s"There is a nullary constructor in this module called '$strName'." +
                        s"Did you mean '$strName()' ?", pat)
                  case None => //Pass
                }

                if (locals.contains(strName))
                  fatal(s"Pattern identifier ${strName} already defined", pat)
                val id = Identifier.fresh(strName)

                (S.IdPattern(id), List((strName, id)))

              }
              case N.LiteralPattern(lit) =>
                (S.LiteralPattern(transformLiteral(lit)), List())

              case N.CaseClassPattern(constr, args) => {
                val (patConstrID, constrSign) =
                  table.getConstructor(constr.module.getOrElse(module), constr.name) match {
                    case None =>
                      fatal(s"Constructor $constr not found", pat)
                    case Some(a) => a
                  }

                //Check same number of arguments as signature
                if (constrSign.argTypes.length != args.length) {
                  fatal(s"Wrong number of arguments for constructor ${constr.name}")
                }

                val (newArgs, recurNames) = args.foldRight(
                  (List[S.Pattern](), List[(String, Identifier)]())
                ) {
                  case (nPattern, (patternAcc, identifierAcc)) => {
                    val (sPattern, innerIdentifiers) = transformPattern(nPattern)
                    (sPattern :: patternAcc, innerIdentifiers ++ identifierAcc)
                  }
                }

                recurNames.groupBy(_._2.name).values.foreach(ls => if(ls.length > 1)
                  fatal(s"Multiple definitions of ${ls.head._1} in pattern", pat)
                )

                (S.CaseClassPattern(patConstrID, newArgs), recurNames)
              }
            }
          }

          def transformCase(cse: N.MatchCase) = {
            val N.MatchCase(pat, rhs) = cse
            val (newPat, moreLocals) = transformPattern(pat)
            //println(s"Locals $moreLocals")
            //No new name to return, they are local to
            S.MatchCase(newPat, transformExpr(rhs)(module, (params, locals ++ moreLocals)))

          }

          S.Match(transformExpr(scrut), cases.map(transformCase))

        case N.Variable(name) => {
          val idName = locals.get(name).orElse(params.get(name)) match {
            case Some(id) => id
            case None => fatal(s"Variable $name not found", expr)
          }
          S.Variable(idName)
        }

        // Literals
        case N.IntLiteral(value) => S.IntLiteral(value)
        case N.BooleanLiteral(v) => S.BooleanLiteral(v)
        case N.StringLiteral(v) => S.StringLiteral(v)
        case N.UnitLiteral() => S.UnitLiteral()
        case N.CharLiteral(c) => S.CharLiteral(c)

        // Binary operators
        case N.Plus(lhs, rhs) => S.Plus(transformExpr(lhs), transformExpr(rhs))
        case N.Minus(lhs, rhs) => S.Minus(transformExpr(lhs), transformExpr(rhs))
        case N.Times(lhs, rhs) => S.Times(transformExpr(lhs), transformExpr(rhs))
        case N.Div(lhs, rhs) => S.Div(transformExpr(lhs), transformExpr(rhs))
        case N.Mod(lhs, rhs) => S.Mod(transformExpr(lhs), transformExpr(rhs))
        case N.LessThan(lhs, rhs) => S.LessThan(transformExpr(lhs), transformExpr(rhs))
        case N.LessEquals(lhs, rhs) => S.LessEquals(transformExpr(lhs), transformExpr(rhs))
        case N.And(lhs, rhs) => S.And(transformExpr(lhs), transformExpr(rhs))
        case N.Or(lhs, rhs) => S.Or(transformExpr(lhs), transformExpr(rhs))
        case N.Equals(lhs, rhs) => S.Equals(transformExpr(lhs), transformExpr(rhs))
        case N.Concat(lhs, rhs) => S.Concat(transformExpr(lhs), transformExpr(rhs))

        // Unary operators
        case N.Not(e) => S.Not(transformExpr(e))
        case N.Neg(e) => S.Neg(transformExpr(e))

        // Function/constructor call
        case N.Call(qname, args) => {
          val (id, sig) = table.getFunction(qname.module.getOrElse(module), qname.name) match {
            case Some(value) => value
            case None => table.getConstructor(qname.module.getOrElse(module), qname.name) match {
              case Some(value) => value
              case None => fatal(s"Function or constructor $qname not found", expr)
            }
          }
          if (sig.argTypes.length != args.length) {
            fatal(s"Wrong number of arguments for function/constructor $qname", expr)
          }
          S.Call(id, args map transformExpr)
        }

        case N.Sequence(e1, e2) => {
          S.Sequence(transformExpr(e1), transformExpr(e2))
        }

        case N.Let(df, value, body) => {

          locals.get(df.name) match {
            case Some(prevDef) =>
              fatal(s"Variable redefinition: ${df.name}", expr)
            case None =>
          }
          val identifier = Identifier.fresh(df.name)
          val valDef = S.ParamDef(identifier, S.TypeTree(transformType(df.tt, module)))
          val newValue = transformExpr(value)
          val newBody = transformExpr(body)(module, (params, locals + (df.name -> identifier)))
          S.Let(valDef, newValue, newBody)
        }

        case N.Ite(cond, thenn, elze) => {
          val newCond = transformExpr(cond)
          val newThen = transformExpr(thenn)
          val newElse = transformExpr(elze)
          S.Ite(newCond, newThen, newElse)
        }
        case N.Slice(e, start, end, step) =>
          S.Slice(transformExpr(e), start.map(s => transformExpr(s)), end.map(s => transformExpr(s)), step.map(s => transformExpr(s)))

        case N.Error(msg) => S.Error(transformExpr(msg))

        case _ =>
          fatal("Unknown expression (PARSER & LEXER FAILED IN THEIR DUTY)", expr)
      }
      res.setPos(expr)
    }

    // Putting it all together to construct the final program for step 6.
    val newProgram = S.Program(
      p.modules map { case mod@N.ModuleDef(name, defs, optExpr) =>
        S.ModuleDef(
          table.getModule(name).get,
          defs map (transformDef(_, name)),
          optExpr map (transformExpr(_)(name, (Map(), Map())))
        ).setPos(mod)
      }
    ).setPos(p)

    (newProgram, table)

  }
}
