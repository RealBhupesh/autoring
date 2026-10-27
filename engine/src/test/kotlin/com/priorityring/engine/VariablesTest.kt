package com.priorityring.engine

import com.priorityring.engine.variables.ClockVariables
import com.priorityring.engine.variables.CompositeBuiltins
import com.priorityring.engine.variables.VariableTemplate
import com.priorityring.engine.variables.Variables
import kotlin.test.Test
import kotlin.test.assertEquals

class VariablesTest {
    private val vars = Variables(mapOf("sender" to "Rahul", "text" to "Server down", "empty" to null))

    @Test
    fun `substitutes known variables`() {
        assertEquals("Urgent message from Rahul: Server down", vars.render("Urgent message from {sender}: {text}"))
    }

    @Test
    fun `unknown and null variables render empty`() {
        assertEquals("[][]", vars.render("[{nope}][{empty}]"))
    }

    @Test
    fun `non-identifier braces are left alone so JSON bodies work`() {
        assertEquals("""{"text":"Server down","n":{ }}""", vars.render("""{"text":"{text}","n":{ }}"""))
        assertEquals("{1abc} { sender } {}", vars.render("{1abc} { sender } {}"))
    }

    @Test
    fun `double brace escapes a literal brace`() {
        assertEquals("{sender} = Rahul", vars.render("{{sender} = {sender}"))
    }

    @Test
    fun `dotted names are allowed`() {
        assertEquals("x", Variables(mapOf("notification.title" to "x")).render("{notification.title}"))
    }

    @Test
    fun `referenced names are listed`() {
        assertEquals(setOf("a", "b.c"), VariableTemplate.referencedNames("{a} {b.c} {{d} {1x}"))
    }

    @Test
    fun `trigger values win over built-ins, built-ins fill the rest`() {
        val clock = ClockVariables { at(2026, 10, 27, 21, 5) }
        val v = Variables(mapOf("time" to "overridden"), CompositeBuiltins(listOf(clock)))
        assertEquals("overridden 2026-10-27", v.render("{time} {date}"))
        assertEquals("21:05", Variables(emptyMap(), clock).render("{time}"))
    }
}
