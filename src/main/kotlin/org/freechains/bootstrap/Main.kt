package org.freechains.bootstrap

import org.freechains.cli.main_cli
import org.freechains.cli.main_cli_assert
import org.freechains.common.*
import org.freechains.host.main_host
import org.freechains.host.main_host_assert
import java.io.File
import kotlin.system.exitProcess

val help = """
freechains-bootstrap $VERSION

Usage:
    freechains-bootstrap init <peer> <chain> <shared>
    freechains-bootstrap start

Options:
    --help          displays this help
    --version       displays version information
    --port          port to connect [default: $PORT_8330]

More Information:

    http://www.freechains.org/

    Please report bugs at <http://github.com/Freechains/README/>.
"""

fun main (args: Array<String>) {
    main_bootstrap(args).let { (ok,msg) ->
        if (ok) {
            if (msg.isNotEmpty()) {
                println(msg)
            }
        } else {
            System.err.println(msg)
            exitProcess(1)
        }
    }
}

fun main_bootstrap (args: Array<String>) : Pair<Boolean,String> {
    return catch_all("freechains-bootstrap ${args.joinToString(" ")}") {
        val (cmds, opts) = args.cmds_opts()
        val port = if (opts.containsKey("--port")) opts["--port"]!!.toInt() else PORT_8330
        val port_ = "--port=$port"

        @Suppress("UNREACHABLE_CODE")
        when {
            opts.containsKey("--help") -> Pair(true, help)
            else -> {
                val path = main_host_assert(arrayOf(port_, "path"))
                when (cmds[0]) {
                    "start" -> {
                        assert_(cmds.size == 1)
                        val chain = File(path)
                            .list()!!
                            .first { f -> f.endsWith(".bootstrap") }
                            .dropLast(".bootstrap".length)
                        Chain(path, chain, port)
                        while (true);
                        Pair(true, "")
                    }
                    "init" -> {
                        assert_(cmds.size == 4)
                        val chain = cmds[2]
                        val host = "--host=localhost:$port"
                        assert_(chain.startsWith("\$bootstrap."))
                        main_cli(arrayOf(host, "chains", "join", chain, cmds[3]))
                        Chain(path, chain, port)
                        main_cli_assert(arrayOf(host, "peer", cmds[1], "recv", chain))
                        while (true);
                        Pair(true, "")
                    }
                    else -> Pair(false, "!")
                }
            }
        }
    }
}
