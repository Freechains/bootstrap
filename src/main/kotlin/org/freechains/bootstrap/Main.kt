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
    --version       displays software version
    --port          port to connect [default: $PORT_8330]

More Information:

    http://www.freechains.org/

    Please report bugs at <http://github.com/Freechains/README/>.
"""

fun main (args: Array<String>) {
    main_ { main_bootstrap(args) }
}

fun main_bootstrap_assert (args: Array<String>) : String {
    return main_assert_ { main_bootstrap(args) }
}

fun main_bootstrap (args: Array<String>) : Pair<Boolean,String> {
    return main_catch_("freechains-bootstrap", VERSION, help, args) { cmds, opts ->
        val port = opts["--port"]?.toInt() ?: PORT_8330
        val port_ = "--port=$port"

        @Suppress("UNREACHABLE_CODE")
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
                assert_(chain.startsWith("\$bootstrap."))
                main_cli(arrayOf(port_, "chains", "join", chain, cmds[3]))
                Chain(path, chain, port)
                main_cli_assert(arrayOf(port_, "peer", cmds[1], "recv", chain))
                while (true);
                Pair(true, "")
            }
            else -> Pair(false, "!")
        }
    }
}
