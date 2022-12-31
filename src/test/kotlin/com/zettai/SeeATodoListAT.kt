package com.zettai

import org.http4k.client.JettyClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import org.http4k.server.asServer

class SeeATodoListAT {
    @Test
    fun `List owners can see their lists`() {
        val user = "Frank"
        val listName = "shopping"
        val foodToBuy = listOf("carrots", "apples", "milk")

        startTheApplication(user, listName, foodToBuy)

        val list = getToDoList(user, listName)


        expectThat(list.listName.name).isEqualTo(listName)
        expectThat(list.items.map(ToDoItem::description)).isEqualTo(foodToBuy)
    }

    private fun getToDoList(user: String, listName: String): ToDoList {
        val client = JettyClient()
        val response = client(Request(Method.GET, "http://localhost:8081/todo/$user/$listName"))
        return if (response.status == Status.OK) {
            parseResponse(response.bodyString())
        } else {
            fail(response.toMessage())
        }
    }

    private fun parseResponse(html: String): ToDoList {
        val listName = extractListName(html)
        val items = extractItems(html)
        return ToDoList(listName, items)
    }

    private fun extractItems(html: String): List<ToDoItem> =
        "<td>(.*?)<"
            .toRegex()
            .findAll(html)
            .map(::extractItemDesc)
            .map(::ToDoItem)
            .toList()

    private fun extractListName(html: String): ListName =
        "<h2>(.*)<"
            .toRegex()
            .find(html)?.let {
                it.groups[1]?.let(MatchGroup::value)
            }
            .orEmpty()
            .let(::ListName)

    private fun extractItemDesc(matchResult: MatchResult): String {
        return matchResult.groups[1]?.value.orEmpty()
    }

    private fun startTheApplication(user: String, listName: String, items: List<String>) {
        val toDoList = ToDoList(ListName(listName), items.map(::ToDoItem))
        val lists = mapOf(User(user) to listOf(toDoList))
        val server = Zettai(lists).asServer(Jetty(8081))
        server.start()
    }
}