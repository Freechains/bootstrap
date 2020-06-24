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

class Chain (chain: String, port: Int = PORT_8330) {
    private var last: String? = null
    private val chain = chain
    private val port_ = "--port=$port"

    @get:Synchronized
    public val peers : MutableList<String> = mutableListOf()

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
                //println(">>> $name")
                thread {
                    if (name == this.chain) {
                        //println(">>> boot $name")
                        this.boot()
                        this.sync(null)
                    } else {
                        //println(">>> sync $name")
                        this.sync(name)
                    }
                }
            }
        }
    }

    fun sync (chain: String?) {
        val chains=
            if (chain != null) {
                listOf(chain)
            } else {
                main_cli_assert(arrayOf(port_, "chains", "list")).listSplit()
            }
        val peers = synchronized (this) { this.peers.toTypedArray() }

        // each action in sequence (receive then send)
        for (action in arrayOf("recv", "send")) {
            // all peers in parallel
            for (peer in peers) {
                thread {
                    for (c in chains) {
                        //println("-=-=-=- $action $c $port_->$peer")
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
        //println(">>> last = $last")
        if (this.last == null) {
            this.last = main_cli_assert(arrayOf(port_, "chain", this.chain, "genesis"))
        }

        val hs = main_cli_assert(arrayOf(port_, "chain", this.chain, "traverse", "all", this.last!!)).listSplit()
        for (h in hs) {
            val v = main_cli_assert(arrayOf(port_, "chain", this.chain, "get", "payload", h))
            //println(">>> v = $v")
            val cmd = v.split(' ')
            when {
                (cmd[0] == "peers") -> when {
                    (cmd[1] == "add") -> this.peers.add(cmd[2])
                    (cmd[1] == "rem") -> this.peers.remove(cmd[2])
                    else -> error("invalid command")
                }
                (cmd[0] == "chains") -> when {
                    (cmd[1] == "add") -> {
                        //println("-=-=-=- [$port_] JOIN: ${cmd[2]}")
                        if (cmd.size == 4) {
                            main_cli(arrayOf(port_, "chains", "join", cmd[2], cmd[3]))
                        } else {
                            main_cli(arrayOf(port_, "chains", "join", cmd[2]))
                        }
                    }
                    (cmd[1] == "rem") -> main_cli(arrayOf(port_, "chains", "leave", cmd[2]))
                    else -> error("invalid command")
                }
                else -> error("invalid command")
            }
        }
        if (hs.isNotEmpty()) {
            this.last = hs.last()
        }
    }
}