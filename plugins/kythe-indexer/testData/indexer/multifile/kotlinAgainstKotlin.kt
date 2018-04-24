// FILE: A.kt
package a

//- FileA=vname("","test","plugins/kythe-indexer/testData/indexer/multifile","/A.kt","")
//-  .node/kind file
//- FileB=vname("","test","plugins/kythe-indexer/testData/indexer/multifile","/B.kt","")
//-  .node/kind file

//- @A defines/binding ClassA
//- ClassA.node/kind record
//- ClassA.subkind class
//- ClassA childof FileA
abstract class A {
    //- @"B" ref ClassB
    //- ClassB.node/kind record
    //- ClassB childof FileB
    val b: B
}


// FILE: B.kt
package a

//- FileA=vname("","test","plugins/kythe-indexer/testData/indexer/multifile","/A.kt","")
//-  .node/kind file
//- FileB=vname("","test","plugins/kythe-indexer/testData/indexer/multifile","/B.kt","")
//-  .node/kind file

//- @B defines/binding ClassB
//- ClassB.node/kind record
//- ClassB.subkind class
//- ClassB childof FileB
abstract class B {
    //- @"A" ref ClassA
    //- ClassA.node/kind record
    //- ClassA childof FileA
    val a: A
}