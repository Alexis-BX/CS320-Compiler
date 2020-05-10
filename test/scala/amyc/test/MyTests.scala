import java.io.File

import amyc.analyzer.{NameAnalyzer, TypeChecker}
import amyc.codegen.{CodeGen, CodePrinter}
import amyc.parsing.{Lexer, Parser}
import amyc.test.TestSuite
import amyc.utils.Pipeline
import junit.framework.Test

class MyTests extends TestSuite {


  override val pipeline: Pipeline[List[File], Unit] = Lexer andThen
    Parser andThen
    NameAnalyzer andThen
    TypeChecker andThen
    CodeGen andThen
    CodePrinter
  override val baseDir: String = "codegen"
  override val outputExt: String = "txt"
}