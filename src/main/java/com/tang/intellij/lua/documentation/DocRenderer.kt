/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.psi.PsiElement
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.ty.IFunSignature
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITyRenderer

inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
    this.append(prefix)
    body()
    this.append(postfix)
}

inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
    wrap("<$tag>", "</$tag>", body)
}

private fun StringBuilder.appendClassLink(clazz: String) {
    DocumentationManagerUtil.createHyperlink(this, clazz, clazz, true)
}

fun renderTy(sb: StringBuilder, ty: ITy, tyRenderer: ITyRenderer) {
    tyRenderer.render(ty, sb)
}

fun renderSignature(sb: StringBuilder, signature: IFunSignature, tyRenderer: ITyRenderer) {
    val sig = signature.params.map { "${it.name}: ${tyRenderer.render(it.ty)}" }
    sb.append("(${sig.joinToString(", <br>        ")}): ")
    tyRenderer.render(signature.returnTy, sb)
}

fun renderComment(sb: StringBuilder, comment: LuaComment?, tyRenderer: ITyRenderer) {
    if (comment != null) {
        var child: PsiElement? = comment.firstChild

        sb.append("<div class='content'>")
        while (child != null) {
            when (child) {
                is LuaDocClassDef -> renderClassDef(sb, child, tyRenderer)
                is LuaDocTypeDef -> renderTypeDef(sb, child, tyRenderer)
                is LuaDocFieldDef -> {}
                is LuaDocSeeRefTag -> {}
                is LuaDocParamDef -> {}
                is LuaDocReturnDef -> {}
                is LuaDocOverloadDef -> {}
                else -> {
                    val elementType = child.node.elementType
                    if (elementType === LuaDocTypes.STRING) {
                        sb.append(markdownToHtml(child.text))
                    }
                }
            }
            child = child.nextSibling
        }
        sb.append("</div>")

        val sections = StringBuilder()
        sections.append("<table class='sections'>")
        //Tags
        renderTagList(sections, "Version", "version", comment)
        renderTagList(sections, "Author", "author", comment)
        renderTagList(sections, "Since", "Since", comment)
        renderTagList(sections, "Deprecated", "deprecated", comment)
        //Fields
        val fields = comment.findTags(LuaDocFieldDef::class.java)
        renderTagList(sections, "Fields", fields) { renderFieldDef(sections, it, tyRenderer) }
        //Parameters
        val docParams = comment.findTags(LuaDocParamDef::class.java)
        renderTagList(sections, "Parameters", docParams) { renderDocParam(sections, it, tyRenderer) }
        //Returns
        val retTag = comment.findTag(LuaDocReturnDef::class.java)
        retTag?.let { renderTagList(sections, "Returns", listOf(retTag)) { renderReturn(sections, it, tyRenderer) } }
        //Overloads
        val overloads = comment.findTags(LuaDocOverloadDef::class.java)
        renderTagList(sections, "Overloads", overloads) { renderOverload(sections, it, tyRenderer) }
        //See
        val seeTags = comment.findTags(LuaDocSeeRefTag::class.java)
        renderTagList(sections, "See", seeTags) { renderSee(sections, it, tyRenderer) }

        sb.append(sections.toString())
        sb.append("</table>")
    }
}

private fun renderReturn(sb: StringBuilder, returnDef: LuaDocReturnDef, tyRenderer: ITyRenderer) {
    val typeList = returnDef.typeList
    if (typeList != null) {
        val list = typeList.tyList
        if (list.size > 1)
            sb.append("(")
        list.forEachIndexed { index, luaDocTy ->
            renderTypeUnion(if (index != 0) ", " else null, null, sb, luaDocTy, tyRenderer)
            sb.append(" ")
        }
        if (list.size > 1)
            sb.append(")")
        renderCommentString(" - ", null, sb, returnDef.commentString)
    }
}

fun renderClassDef(sb: StringBuilder, def: LuaDocClassDef, tyRenderer: ITyRenderer) {
    val cls = def.type
    sb.append("<pre>")
    sb.append("class ")
    sb.wrapTag("b") { tyRenderer.render(cls, sb) }
    val superClassName = cls.superClassName
    if (superClassName != null) {
        sb.append(" : ")
        sb.appendClassLink(superClassName)
    }
    sb.append("</pre>")
    renderCommentString(" - ", null, sb, def.commentString)
}

private fun renderFieldDef(sb: StringBuilder, def: LuaDocFieldDef, tyRenderer: ITyRenderer) {
    sb.append("${def.name}: ")
    renderTypeUnion(null, null, sb, def.ty, tyRenderer)
    renderCommentString(" - ", null, sb, def.commentString)
}

fun renderDefinition(sb: StringBuilder, block: () -> Unit) {
    sb.append("<div class='definition'><pre>")
    block()
    sb.append("</pre></div>")
}

private fun renderTagList(sb: StringBuilder, title: String, name: String, comment: LuaComment) {
    val tags = comment.findTags(name)
    renderTagList(sb, title, tags) { tagDef ->
        tagDef.commentString?.text?.let { sb.append(it) }
    }
}

private fun <T : LuaDocPsiElement> renderTagList(sb: StringBuilder, name: String, tags: Collection<T>, block: (tag: T) -> Unit) {
    if (tags.isEmpty())
        return
    sb.wrapTag("tr") {
        sb.append("<td valign='top' class='section'><p>$name</p></td>")
        sb.append("<td valign='top'>")
        for (tag in tags) {
            sb.wrapTag("p") {
                block(tag)
            }
        }
        sb.append("</td>")
    }
}

fun renderDocParam(sb: StringBuilder, child: LuaDocParamDef, tyRenderer: ITyRenderer, paramTitle: Boolean = false) {
    val paramNameRef = child.paramNameRef
    if (paramNameRef != null) {
        if (paramTitle)
            sb.append("<b>param</b> ")
        sb.append("<code>${paramNameRef.text}</code> : ")
        renderTypeUnion(null, null, sb, child.ty, tyRenderer)
        renderCommentString(" - ", null, sb, child.commentString)
    }
}

fun renderCommentString(prefix: String?, postfix: String?, sb: StringBuilder, child: LuaDocCommentString?) {
    child?.string?.text?.let {
        if (prefix != null) sb.append(prefix)
        var html = markdownToHtml(it)
        if (html.startsWith("<p>"))
            html = html.substring(3, html.length - 4)
        sb.append(html)
        if (postfix != null) sb.append(postfix)
    }
}

private fun renderTypeUnion(prefix: String?, postfix: String?, sb: StringBuilder, type: LuaDocTy?, tyRenderer: ITyRenderer) {
    if (type != null) {
        if (prefix != null) sb.append(prefix)

        val ty = type.getType()
        renderTy(sb, ty, tyRenderer)

        if (postfix != null) sb.append(postfix)
    }
}

private fun renderOverload(sb: StringBuilder, overloadDef: LuaDocOverloadDef, tyRenderer: ITyRenderer) {
    overloadDef.functionTy?.getType()?.let {
        renderTy(sb, it, tyRenderer)
    }
}

private fun renderTypeDef(sb: StringBuilder, typeDef: LuaDocTypeDef, tyRenderer: ITyRenderer) {
    renderTy(sb, typeDef.type, tyRenderer)
}

private fun renderSee(sb: StringBuilder, see: LuaDocSeeRefTag, tyRenderer: ITyRenderer) {
    see.classNameRef?.resolveType()?.let {
        renderTy(sb, it, tyRenderer)
        see.id?.let {
            sb.append("#${it.text}")
        }
    }
}