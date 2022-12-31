package com.zettai

import org.http4k.core.*
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

data class User(val name: String)
data class ListName(val name: String)
data class ToDoItem(val description: String)
data class ToDoList(val listName: ListName, val items: List<ToDoItem>)

//enum class ToDoStatus { Todo, InProgress, Done, Blocked }
data class HtmlPage(val raw: String)


class Zettai(val lists: Map<User, List<ToDoList>>) : HttpHandler {
    val routes = routes(
        "/todo/{user}/{list}" bind Method.GET to ::showList,
    )

    private fun showList(request: Request): Response =
        request
            .let(::extractListData)
            .let(::fetchListContent)
            .let(::renderHtml)
            .let(::createResponse)


    fun extractListData(request: Request): Pair<User, ListName> {
        val user = request.path("user").orEmpty()
        val list = request.path("list").orEmpty()

        return User(user) to ListName(list)
    }

    fun fetchListContent(listId: Pair<User, ListName>): ToDoList =
        lists[listId.first]
            ?.firstOrNull() { it.listName == listId.second }
            ?: error("List unknown")

    fun renderHtml(todoList: ToDoList): HtmlPage =
        HtmlPage(
            """
            <html>
                <body>
                    <h1>Zettai</h1>
                    <h2>${todoList.listName.name}</h2>
                <table>
                    <tbody>${renderItems(todoList.items)}</tbody>
                </table>
                </body>
            </html>
        """.trimIndent()
        )

    fun renderItems(items: List<ToDoItem>) =
        items.map {
            """<tr><td>${it.description}</td></tr>""".trimIndent()
        }.joinToString("")

    fun createResponse(html: HtmlPage): Response =
        Response(Status.OK).body(html.raw)

    override fun invoke(request: Request): Response = routes(request)
}