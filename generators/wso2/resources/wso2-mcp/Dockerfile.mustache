# The WSO2 MCP server, packaged as an image.
#
# Unlike the provider image beside it, this one IS run: an MCP client starts
# the container and speaks the protocol over its stdin and stdout, so nothing
# may be printed to stdout that is not a protocol message.
#
# The build compiles the COMMITTED generated code -- bin/generate is not run
# here. Regeneration needs the patched openapi-generator, and what is committed
# is what ships.
FROM golang:1.26-bookworm AS build

WORKDIR /src
COPY . /src

RUN CGO_ENABLED=0 go build -trimpath -o /out/mcp-server-wso2 .

FROM debian:bookworm-slim

# TLS roots: the server talks to a WSO2 over HTTPS, and a scratch image has
# none -- every call would fail certificate verification.
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /out/mcp-server-wso2 /usr/local/bin/mcp-server-wso2

# stdio transport: the protocol IS this process's stdin and stdout.
ENTRYPOINT ["/usr/local/bin/mcp-server-wso2"]
