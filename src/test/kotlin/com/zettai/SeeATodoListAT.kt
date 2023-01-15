package com.zettai

import org.http4k.client.JettyClient
import org.http4k.core.*
import org.http4k.filter.ClientFilters
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.opentest4j.AssertionFailedError
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

interface Actions {
    fun getToDoList(user: String, listName: String): ToDoList?
}

typealias Step = Actions.() -> Unit

class ApplicationForAT(val client: HttpHandler, private val server: AutoCloseable) : Actions {
    override fun getToDoList(user: String, listName: String): ToDoList {
        val response = client(Request(Method.GET, "/todo/$user/$listName"))
        return if (response.status == Status.OK) {
            parseResponse(response.bodyString())
        } else {
            fail(response.toMessage())
        }
    }

    fun runScenario(vararg steps: Step) {
        server.use {
            steps.onEach { it(this) }
        }
    }

    private fun parseResponse(html: String): ToDoList {
        val listName = extractListName(html)
        val items = extractItems(html)
        return ToDoList(listName, items)
    }

    private fun extractListName(html: String): ListName =
        "<h2>(.*)<"
            .toRegex()
            .find(html)?.let {
                it.groups[1]?.let(MatchGroup::value)
            }
            .orEmpty()
            .let(::ListName)

    private fun extractItems(html: String): List<ToDoItem> =
        "<td>(.*?)<"
            .toRegex()
            .findAll(html)
            .map(::extractItemDesc)
            .map(::ToDoItem)
            .toList()

    private fun extractItemDesc(matchResult: MatchResult): String {
        return matchResult.groups[1]?.value.orEmpty()
    }
}

interface ScenarioActor {
    val name: String
}

class ToDoListOwner(override val name: String) : ScenarioActor {
    fun canSeeTheList(listName: String, items: List<String>): Step = {
        val expectedList = createList(listName, items)
        val list = getToDoList(name, listName)
        expectThat(list).isEqualTo(expectedList)
    }

    fun cannotSeeTheList(listName: String): Step = {
        expectThrows<AssertionFailedError> {
            getToDoList(name, listName)
        }
    }
}

fun ToDoListOwner.asUser(): User = User(name)

private fun createList(listName: String, items: List<String>) = ToDoList(ListName(listName), items.map(::ToDoItem))

class SeeATodoListAT {
    private val frank = ToDoListOwner("Frank")
    private val shoppingItems = listOf("carrots", "apples", "milk")
    private val frankList = createList("shopping", shoppingItems)

    private val bob = ToDoListOwner("Bob")
    private val gardenItems = listOf("fix the fence", "mowing the lawn")
    private val bobList = createList("gardening", gardenItems)

    private val lists: Map<User, List<ToDoList>> = mapOf(
        frank.asUser() to listOf(frankList),
        bob.asUser() to listOf(bobList)
    )

    @Test
    fun `List owners can see their lists`() {
        val app = startTheApplication(lists)
        app.runScenario(
            frank.canSeeTheList("shopping", shoppingItems),
            bob.canSeeTheList("gardening", gardenItems)
        )
    }

    @Test
    fun `Only owners can see their lists`() {
        val app = startTheApplication(lists)

        app.runScenario(
            frank.cannotSeeTheList("gardening"),
            bob.cannotSeeTheList("shopping")
        )
    }

    private fun startTheApplication(lists: Map<User, List<ToDoList>>): ApplicationForAT {
        val port = 8081
        val server = Zettai(lists).asServer(Jetty(port))
        server.start()

        val client = ClientFilters
            .SetBaseUriFrom(Uri.of("http://localhost:$port/"))
            .then(JettyClient())

        return ApplicationForAT(client, server)
    }
}
