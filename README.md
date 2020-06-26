# Bootstrap Tool - Freechains

The *bootstrap tool* sets up a dedicated chain to persist peers and chains of
interest to be replicated in the network.

- A bootstrap chain is just a normal chain, but it is recommended to be private
  so that only the key holders can read and write to it:

```
$ freechains-host start <path> &            # starts freechains, if not yet started
$ freechains crypto shared <password>       # creates a key for the bootstrap chain
$ freechains chains join \$bootstrap <KEY>  # creates a private chain
$ freechains-bootstrap \$bootstrap &        # keeps the host up-to-date
```

The tool should be started every time freechains is started (lines `1` and `4`
above)

- Posts in a bootstrap chain are interpreted as commands that can add/remove
  peers/chains of interest:

```
freechains chain \$bootstrap post inline "peers add lcc-uerj.duckdns.org"
freechains chain \$bootstrap post inline "peers add francisco-santanna.duckdns.org"
freechains chain \$bootstrap post inline "chains add #"
freechains chain \$bootstrap post inline "chains add #br"
freechains chain \$bootstrap post inline "chains add @7EA6E8E2DD5035AAD58AE761899D2150B9FB06F0C8ADC1B5FE817C4952AC06E6"
```

Follows the list of available commands:

- `peers add <addr:port>`
- `peers rem <addr:port>`
- `chains add <name> [<key>]`
- `chains rem <name>`

The tool interprets all existing posts in the chain from the oldest to newest
and then listens for new posts which are also interpreted in real time.
The tool applies a `join` to added chains and a `leave` to removed chains
automatically.
It also listens for incoming data in all chains and synchronizes them with the
added peers.
