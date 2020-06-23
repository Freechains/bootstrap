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

class Chain (chain: String, port: Int = PORT_8330) {
    private var last: String? = null
    private val chain = chain
    private val port_ = "--port=$port"
    private val store = Store(mutableListOf(), mutableListOf())

    init {
        assert(chain.startsWith("\$bootstrap."))
        thread { this.boot() ; this.sync(null) }
        thread {
            val socket = Socket("localhost", port)
            val writer = DataOutputStream(socket.getOutputStream()!!)
            val reader = DataInputStream(socket.getInputStream()!!)
            writer.writeLineX("$PRE chains listen")
            while (true) {
                val (_,name) = reader.readLineX().listSplit()
                println(">>> $name")
                thread {
                    if (name == this.chain) {
                        println(">>> boot $name")
                        this.boot()
                        this.sync(null)
                    } else {
                        println(">>> sync $name")
                        this.sync(name)
                    }
                }
            }
        }
    }

    @Synchronized
    fun <T> read (f: (Store)->T) : T {
        return f(this.store)
    }

    fun sync (chain: String?) {
        val (peers,chains) = this.read {
            Pair(it.peers, if (chain == null) it.chains.map { it.first } else listOf(chain))
        }

        // each action in sequence (receive then send)
        for (action in arrayOf("recv", "send")) {
            // all peers in parallel
            for (peer in peers) {
                thread {
                    for (c in chains) {
                        //println(">>> $action ${chain.first} $port_->$peer")
                        main_cli(arrayOf(port_, "peer", peer, action, c))
                    }
                }
            }
            // wait socket timeout to go to next action
            Thread.sleep(T5S_socket * 3 / 2)
        }
    }

    // read bootstrap chain, update store, join chains, notify listeners, synchronize with the world
    @Synchronized
    fun boot () {
        println(">>> last = $last")
        if (this.last == null) {
            this.last = main_cli_assert(arrayOf(port_, "chain", this.chain, "genesis"))
        }

        val hs = main_cli_assert(arrayOf(port_, "chain", this.chain, "traverse", "all", this.last!!)).listSplit()
        for (h in hs) {
            val v = main_cli_assert(arrayOf(port_, "chain", this.chain, "get", "payload", h))
            println(">>> v = $v")
            val cmd = v.split(' ')
            when {
                (cmd[0] == "peers") -> when {
                    (cmd[1] == "add") -> this.store.peers.add(cmd[2])
                    (cmd[2] == "rem") -> TODO()
                    else -> error("invalid command")
                }
                (cmd[0] == "chains") -> when {
                    (cmd[1] == "add") -> this.store.chains.add(Pair(cmd[2], if (cmd.size==4) cmd[3] else null))
                    (cmd[2] == "rem") -> TODO()
                    else -> error("invalid command")
                }
                else -> error("invalid command")
            }
        }
        if (hs.isNotEmpty()) {
            this.last = hs.last()
        }

        // join all chains
        this.store.chains
            .map {
                thread {
                    println(">>> JOIN: ${it.first}")
                    main_cli (
                        arrayOf(port_, "chains", "join", it.first)
                            .plus (if (it.second==null) emptyArray() else arrayOf(it.second!!))
                    )
                }
            }
            .forEach { it.join() }
    }
}