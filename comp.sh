mv wasmout/test.wat wasmout/my_temp
java -jar reference_compiler/amyc_2.12-1.6.jar library/Std.scala examples/test.scala
mv wasmout/my_temp wasmout/my_test.wat
echo "Difference between reference and personal code generator: "
diff wasmout/test.wat wasmout/my_test.wat
