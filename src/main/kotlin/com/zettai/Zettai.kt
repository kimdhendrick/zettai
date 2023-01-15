package com.zettai

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

data class User(val name: String)
data class ListName(val name: String)
data class ToDoItem(val description: String)
data class ToDoList(val listName: ListName, val items: List<ToDoItem>)

// enum class ToDoStatus { Todo, InProgress, Done, Blocked }
data class HtmlPage(val raw: String)

interface ZettaiHub {
    fun getList(user: User, listName: ListName): ToDoList?
}

class ToDoListHub(private val lists: Map<User, List<ToDoList>>) : ZettaiHub {
    override fun getList(user: User, listName: ListName): ToDoList? =
        lists[user]
            ?.firstOrNull { it.listName == listName }
}

class Zettai(private val hub: ZettaiHub) : HttpHandler {
    val routes = routes(
        "/todo/{user}/{list}" bind Method.GET to ::showList,
    )

    private fun showList(request: Request): Response =
        request
            .let(::extractListData)
            .let(::fetchListContent)
            .let(::renderHtml)
            .let(::createResponse)

    private fun extractListData(request: Request): Pair<User, ListName> {
        val user = request.path("user").orEmpty()
        val list = request.path("list").orEmpty()

        return User(user) to ListName(list)
    }

    private fun fetchListContent(listId: Pair<User, ListName>): ToDoList =
        hub.getList(listId.first, listId.second)
            ?: error("List unknown")

    private fun renderHtml(todoList: ToDoList): HtmlPage =
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
            """.trimIndent(),
        )

    private fun renderItems(items: List<ToDoItem>) =
        items.joinToString("") {
            """<tr><td>${it.description}</td></tr>""".trimIndent()
        }

    private fun createResponse(html: HtmlPage): Response =
        Response(Status.OK).body(html.raw)

    override fun invoke(request: Request): Response = routes(request)
}
