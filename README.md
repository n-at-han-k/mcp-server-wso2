# mcp-server-wso2

An MCP server over WSO2 Identity Server, generated from the API descriptions
WSO2 publishes with the server itself. Same technique as
`terraform-provider-wso2` beside it — the same four documents, the same patched
openapi-generator, a different thing out the other end.

```bash
nix develop
bin/generate        # four WSO2 documents in, this repo out
```

Everything under `internal/` and `main.go` is generated and **committed**: the
Dockerfile compiles what is in the tree, not what a regeneration would produce.
Run `bin/generate`, read the diff, commit it.

## Where it comes from

Four documents WSO2 publishes with the server itself — tenant management,
organization management, application management, identity provider (connection)
management — merged into one and read in a single pass. **147 tools**, one per
operation. Nothing is filtered: an operation left out is a thing nobody can
call, and a client that finds a hundred-odd tools too many can hide some.

Those documents live in [wso2/identity-api-server], cloned into `reference/`.
`reference/` is gitignored — it is upstream, read only.

```bash
mkdir -p reference && cd reference
git clone --depth 1 --filter=blob:none https://github.com/wso2/identity-api-server.git
```

The four are separate APIs and openapi-generator reads one document per run, so
`bin/merge-specs` puts them together first — the same script the provider uses,
for the same reason: they disagree about what `Error`, `Link`, `Attribute` and
`Certificate` are, and more than one spells a share operation with the same
`operationId`, which makes a merged document *invalid* rather than merely
ambiguous.

## Running it

```bash
WSO2_BASE_URL=https://is.example.com/api/server/v1 \
WSO2_TOKEN=... \
  go run .
```

| variable | what |
|---|---|
| `WSO2_BASE_URL` | the server **and** the API prefix; required |
| `WSO2_TOKEN` | a bearer token |
| `WSO2_USERNAME`, `WSO2_PASSWORD` | basic auth, used when no token is set |
| `WSO2_INSECURE` | `true` skips certificate verification, for a WSO2 on its own self-signed certificate |

The transport is stdio, so **stdout is the protocol**: every diagnostic the
server prints goes to stderr, and anything else written there is a parse error
at the client.

The tenant API is super-tenant scoped (`/api/server/v1`) while the organization
API is tenant scoped (`/t/{tenant-domain}/api/server/v1`). One client holds one
base URL, so reaching both means running this server twice — the same split the
provider has.

## The generator

`-g wso2-mcp` is a `DefaultCodegen` subclass, built by
`nix build .#openapi-generator-wso2-mcp` — `javac` against the packaged CLI's
own jar and an SPI entry, no Maven and no checkout of the generator.

Upstream writes one API file per **tag** and a typed client to go with it. An
MCP server wants neither: every tool is dispatched through the same code path —
fill the path template, attach the query, send the body — so what has to be
produced is a **table**, and a table is one file. `addOperationToGroup` keys
every operation into one group and `toApiFilename` names the file; `-t
templates/` reaches neither, which is the whole reason for the Java.

What a tool *accepts* is a different matter. An MCP client is handed
`inputSchema` verbatim, so a `$ref` pointing into a document it never saw
resolves to nothing — the schema has to be **dereferenced**, which is a schema
walk no template can do. That travels in the document instead: `bin/annotate`
writes `x-mcp-schema`, `x-mcp-path-params`, `x-mcp-query-params` and
`x-mcp-body` onto each operation, and `tools.mustache` prints them. The
generator stays about structure.

WSO2's schemas point at each other in both directions — an `Application` holds
an `AuthenticationSequence` holding steps holding authenticators — so the
dereference stops at a `$ref` already open in the same branch and leaves a bare
`object` there. A client that recursed with us would never finish.

`Dockerfile`, `.dockerignore` and `.github/workflows/build-image.yml` are
generated too, from `generators/wso2/resources/wso2-mcp/`. A server that cannot
be deployed is not finished.

## What it does not do yet

- **No pagination help.** A list tool answers what WSO2 answered, cursor and
  all; nothing follows a `next` link on the model's behalf.
- **No OAuth.** A token is passed in, not obtained — there is no client
  credentials grant here, so something else has to mint it and keep it fresh.
- **Every tool is offered at once.** 147 is a lot of tools for one client;
  the documents describe 147 operations, and choosing among them is a decision
  this repo does not make.
- **Arguments are validated by WSO2, not here.** `Server.AddTool` takes the
  schema as written and does no checking, so a bad argument comes back as a
  400 with WSO2's own error code rather than a local complaint.
- **`HEAD` tools answer a status and nothing else**, because that is what a
  `HEAD` is.

[wso2/identity-api-server]: https://github.com/wso2/identity-api-server
