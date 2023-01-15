package com.zettai

import com.ubertob.pesticide.core.DDT
import com.ubertob.pesticide.core.DdtActions
import com.ubertob.pesticide.core.DdtActor
import com.ubertob.pesticide.core.DdtProtocol
import com.ubertob.pesticide.core.DdtStep
import com.ubertob.pesticide.core.DomainDrivenTest
import com.ubertob.pesticide.core.DomainOnly
import com.ubertob.pesticide.core.DomainSetUp
import com.ubertob.pesticide.core.Http
import com.ubertob.pesticide.core.Ready
import org.http4k.client.JettyClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.jupiter.api.fail
import org.opentest4j.AssertionFailedError
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isNotNull

typealias ZettaiDDT = DomainDrivenTest<ZettaiActions>

fun allActions() = setOf(
    DomainOnlyActions(),
    HttpActions(),
)

interface ZettaiActions : DdtActions<DdtProtocol> {
    fun getToDoList(user: User, listName: ListName): ToDoList?
}

class DomainOnlyActions() : ZettaiActions {
    override val protocol: DdtProtocol = DomainOnly
    override fun prepare() = Ready
    private val lists: Map<User, List<ToDoList>> = emptyMap()
    private val hub = ToDoListHub(lists)
    override fun getToDoList(user: User, listName: ListName): ToDoList? =
        hub.getList(user, listName)
}

class HttpActions(env: String = "local") : ZettaiActions {
    private val zettaiPort = 8000
    private val server = Zettai(ToDoListHub(emptyMap())).asServer(Jetty(zettaiPort))
    val client = JettyClient()

    override val protocol: DdtProtocol = Http(env)
    override fun prepare(): DomainSetUp {
        server.start()
        return Ready
    }

    override fun tearDown(): HttpActions = also { server.stop() }

    private fun callZettai(method: Method, path: String): Response =
        client(Request(method, "http://localhost:$zettaiPort/$path"))

    override fun getToDoList(user: User, listName: ListName): ToDoList {
        val response = callZettai(Method.GET, "/todo/${user.name}/${listName.name}")
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

class ToDoListOwnerDDT(override val name: String) : DdtActor<ZettaiActions>() {
    private val user = User(name)

    fun `can see #listname with #itemnames`(listName: String, expectedItems: List<String>) =
        step(listName, expectedItems) {
            val list = getToDoList(user, ListName(listName))
            expectThat(list)
                .isNotNull()
                .itemNames
                .containsExactlyInAnyOrder(expectedItems)
        }

    private val Assertion.Builder<ToDoList>.itemNames
        get() = get { items.map { it.description } }

    fun `starts with a list`(listName: String, items: List<String>) {
        TODO("Not yet implemented")
    }

    fun `cannot see #listname`(listName: String): DdtStep<ZettaiActions, *> = step {
        expectThrows<AssertionFailedError> {
            getToDoList(User(name), ListName(listName))
        }
    }
}

class SeeATodoListDDT : ZettaiDDT(allActions()) {
    private val frank by NamedActor(::ToDoListOwnerDDT)
    private val bob by NamedActor(::ToDoListOwnerDDT)
    private val tom by NamedActor(::ToDoListOwnerDDT)
    private val adam by NamedActor(::ToDoListOwnerDDT)
    private val shoppingListName = "shopping"
    private val shoppingItems = listOf("carrots", "apples", "milk")

    private val gardenListName = "gardening"
    private val gardenItems = listOf("fix the fence", "mowing the lawn")

    @DDT
    fun `List owners can see their lists`() = ddtScenario {
        setUp {
            frank.`starts with a list`(shoppingListName, shoppingItems)
            bob.`starts with a list`(gardenListName, gardenItems)
        }.thenPlay(
            frank.`can see #listname with #itemnames`(shoppingListName, shoppingItems),
            bob.`can see #listname with #itemnames`(gardenListName, gardenItems),
        )
    }

    @DDT
    fun `Only owners can see their lists`() = ddtScenario {
        setUp {
            tom.`starts with a list`(shoppingListName, shoppingItems)
            adam.`starts with a list`(gardenListName, gardenItems)
        }.thenPlay(
            tom.`cannot see #listname`(gardenListName),
            adam.`cannot see #listname`(shoppingListName),
        )
    }
}
