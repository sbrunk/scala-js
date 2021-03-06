/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.scalajs.linker.backend.emitter

import org.scalajs.ir.Position

import org.scalajs.linker.backend.javascript.Trees._

/** Collection of tree generators that are used across the board.
 *  This class is fully stateless.
 *
 *  Also carries around config (semantics and esFeatures).
 */
private[emitter] final class JSGen(val config: Emitter.Config) {

  import config._

  val useClasses = esFeatures.useECMAScript2015

  val useArrowFunctions = esFeatures.useECMAScript2015

  val useLets = esFeatures.useECMAScript2015

  def genConst(name: Ident, rhs: Tree)(implicit pos: Position): LocalDef =
    genLet(name, mutable = false, rhs)

  def genLet(name: Ident, mutable: Boolean, rhs: Tree)(
      implicit pos: Position): LocalDef = {
    if (useLets)
      Let(name, mutable, Some(rhs))
    else
      VarDef(name, Some(rhs))
  }

  def genEmptyMutableLet(name: Ident)(implicit pos: Position): LocalDef =
    genEmptyLet(name, mutable = true)

  def genEmptyImmutableLet(name: Ident)(implicit pos: Position): LocalDef =
    genEmptyLet(name, mutable = false)

  private def genEmptyLet(name: Ident, mutable: Boolean)(
      implicit pos: Position): LocalDef = {
    if (useLets)
      Let(name, mutable, rhs = None)
    else
      VarDef(name, rhs = None)
  }

  def genBracketSelect(qual: Tree, item: Tree)(implicit pos: Position): Tree = {
    item match {
      case StringLiteral(name) if optimizeBracketSelects &&
          Ident.isValidJSIdentifierName(name) && name != "eval" =>
        /* We exclude "eval" because we do not want to rely too much on the
         * strict mode peculiarities of eval(), so that we can keep running
         * on VMs that do not support strict mode.
         */
        DotSelect(qual, Ident(name))
      case _ =>
        BracketSelect(qual, item)
    }
  }

  def genIdentBracketSelect(qual: Tree, item: String)(
      implicit pos: Position): Tree = {
    require(item != "eval")
    if (optimizeBracketSelects)
      DotSelect(qual, Ident(item))
    else
      BracketSelect(qual, StringLiteral(item))
  }

  def genArrowFunction(args: List[ParamDef], body: Tree)(
      implicit pos: Position): Function = {
    Function(useArrowFunctions, args, body)
  }

  def genDefineProperty(obj: Tree, prop: Tree, descriptor: List[(String, Tree)])(
      implicit pos: Position): WithGlobals[Tree] = {
    val descriptorTree =
        ObjectConstr(descriptor.map(x => StringLiteral(x._1) -> x._2))

    val tree = Apply(
        genIdentBracketSelect(VarRef(Ident("Object")), "defineProperty"),
        List(obj, prop, descriptorTree))

    WithGlobals(tree, Set("Object"))
  }
}
