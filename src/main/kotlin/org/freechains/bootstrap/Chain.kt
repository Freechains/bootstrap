package org.freechains.bootstrap

import org.freechains.common.*
import org.freechains.cli.main_cli_assert
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.freechains.cli.main_cli
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import kotlin.concurrent.thread

@Serializable
data class Store (
    val peers  : MutableList<String>,
    val chains : MutableList<Pair<String,HKey?>>
)

fun String.jsonToStore (): Store {
    @OptIn(UnstableDefault::class)
    return Json(JsonConfiguration(prettyPrint=true)).parse(Store.serializer(), this)
}

class Chain (root: String, chain: String, host: String = "localhost:$PORT_8330") {
    val cbs: MutableSet<(Store)->Unit> = mutableSetOf()

    private var busy  = false
    private val path  = root + "/" + chain + ".bootstrap"
    private val chain = chain
    private val hhost = "--host=$host"
    private var store = Store(mutableListOf(), mutableListOf())

    private fun toJson (): String {
        @OptIn(UnstableDefault::class)
        return Json(JsonConfiguration(prettyPrint=true)).stringify(Store.serializer(), this.store)
    }

    init {
        this.update()
        thread {
            val (addr,port) = host.hostSplit()
            val socket = Socket(addr,port)
            val writer = DataOutputStream(socket.getOutputStream()!!)
            val reader = DataInputStream(socket.getInputStream()!!)
            writer.writeLineX("$PRE chain $chain listen")
            while (true) {
                reader.readLineX()
                this.update()
            }
        }
    }

    @Synchronized
    fun write (f: (Store)->Unit) {
        f(this.store)
        val json = this.toJson()
        thread {
            main_cli_assert(arrayOf(hhost, "chain", this.chain, "post", "inline", json))
        }
    }

    @Synchronized
    fun <T> read (f: (Store)->T) : T {
        return f(this.store)
    }

    fun sync () {
        for (chain in this.store.chains) {
            this.store.peers
                .map {
                    thread {
                        main_cli(arrayOf(hhost, "peer", it, "send", chain.first))
                        main_cli(arrayOf(hhost, "peer", it, "recv", chain.first))
                    }
                }
                .forEach { it.join() }
        }
    }

    // read bootstrap chain, update store, join chains, notify listeners
    fun update () {
        synchronized (this) {
            assert_(!this.busy) { "bootstrap is busy" }
            this.busy = true
        }

        thread {
            // get last head
            val head = main_cli_assert(arrayOf(hhost, "chain", chain, "heads", "all"))
            assert_(!head.contains(' ')) { "multiple heads" }

            // get last store
            val store =
                if (head.startsWith("0_")) {
                    Store(mutableListOf(), mutableListOf())
                } else {
                    main_cli_assert(arrayOf(hhost, "chain", chain, "get", "payload", head)).jsonToStore()
                }

            // join all chains
            store.chains
                .map {
                    thread {
                        main_cli (
                            arrayOf(hhost, "chains", "join", it.first)
                                .plus (if (it.second==null) emptyArray() else arrayOf(it.second!!))
                        )
                    }
                }
                .forEach { it.join() }

            // save store and notify listeners
            synchronized (this) {
                this.store = store
                File(this.path).writeText(this.toJson())
                this.cbs.forEach { it(this.store) }
                this.busy = false
            }

            this.sync()
        }
    }
}