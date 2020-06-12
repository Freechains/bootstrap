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

class Chain (root: String, chain: String, port: Int = PORT_8330) {
    val cbs: MutableSet<(Store)->Unit> = mutableSetOf()

    private var work  = Pair(false,false)   // working, doagain
    private val path  = root + "/" + chain + ".bootstrap"
    private val chain = chain
    private val port_ = "--port=$port"
    private var store = Store(mutableListOf(), mutableListOf())

    private fun toJson (): String {
        @OptIn(UnstableDefault::class)
        return Json(JsonConfiguration(prettyPrint=true)).stringify(Store.serializer(), this.store)
    }

    init {
        assert(chain.startsWith("\$bootstrap."))
        thread { this.update() }
        thread {
            val socket = Socket("localhost", port)
            val writer = DataOutputStream(socket.getOutputStream()!!)
            val reader = DataInputStream(socket.getInputStream()!!)
            writer.writeLineX("$PRE chain $chain listen")
            while (true) {
                reader.readLineX()
                thread { this.update() }
            }
        }
    }

    @Synchronized
    fun write (f: (Store)->Unit) {
        f(this.store)
        val json = this.toJson()
        thread {
            main_cli_assert(arrayOf(port_, "chain", this.chain, "post", "inline", json))
        }
    }

    @Synchronized
    fun <T> read (f: (Store)->T) : T {
        return f(this.store)
    }

    // read bootstrap chain, update store, join chains, notify listeners
    fun update () {
        synchronized (this) {
            if (this.work.first) {
                this.work = Pair(true,true)
                return
            }
        }

        // get last head
        val head = main_cli_assert(arrayOf(port_, "chain", chain, "heads", "all"))
        assert_(!head.contains(' ')) { "multiple heads" }

        // get last store
        val store =
            if (head.startsWith("0_")) {
                Store(mutableListOf(), mutableListOf())
            } else {
                main_cli_assert(arrayOf(port_, "chain", chain, "get", "payload", head)).jsonToStore()
            }

        // join all chains
        store.chains
            .map {
                thread {
                    //println(">>> JOIN: ${it.first}")
                    main_cli (
                        arrayOf(port_, "chains", "join", it.first)
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
        }

        // sync
        thread {
            // each action in sequence (receive then send)
            for (action in arrayOf("recv","send")) {
                // all peers in parallel
                for (peer in store.peers) {
                    thread {
                        for (chain in store.chains) {
                            //println(">>> $action ${chain.first} $port_->$peer")
                            main_cli(arrayOf(port_, "peer", peer, action, chain.first))
                        }
                    }
                }
                // wait socket timeout to go to next action
                Thread.sleep(T5S_socket*3/2)
            }
        }

        synchronized (this) {
            val repeat = this.work.second
            this.work = Pair(false,false)
            if (repeat) {
                return this.update()
            }
        }

    }
}