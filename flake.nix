{
  # The shell the generator and the server share: openapi-generator writes the
  # Go, Go builds it, an MCP client runs it over stdio.
  description = "WSO2 MCP server, generated from the Identity Server API specs";
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    utils.url = "github:numtide/flake-utils";
  };
  outputs = { self, nixpkgs, utils }:
    (utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        # The hooks a template cannot reach: which operations share a file, and
        # what that file is called. javac against the CLI's own jar and an SPI
        # entry -- no Maven, no checkout of the generator.
        wso2-mcp-codegen = pkgs.stdenv.mkDerivation {
          name = "wso2-mcp-codegen";
          src = ./generators/wso2;

          nativeBuildInputs = [ pkgs.jdk ];

          buildPhase = ''
            mkdir -p classes
            javac -nowarn -proc:none \
              -cp ${pkgs.openapi-generator-cli}/share/java/openapi-generator-cli.jar \
              -d classes $(find src -name '*.java')
            cp -r resources/. classes/
            jar cf wso2-mcp-codegen.jar -C classes .
          '';

          installPhase = ''
            install -Dm644 wso2-mcp-codegen.jar $out/share/java/wso2-mcp-codegen.jar
          '';
        };

        # The packaged CLI runs `java -jar`, which ignores -cp; a generator on
        # the classpath needs the main class named.
        openapi-generator-wso2-mcp = pkgs.writeShellApplication {
          name = "openapi-generator-wso2-mcp";
          runtimeInputs = [ pkgs.jre ];
          text = ''
            exec java -cp ${wso2-mcp-codegen}/share/java/wso2-mcp-codegen.jar:${pkgs.openapi-generator-cli}/share/java/openapi-generator-cli.jar \
              org.openapitools.codegen.OpenAPIGenerator "$@"
          '';
        };

      in
      {
        packages = { inherit wso2-mcp-codegen openapi-generator-wso2-mcp; };

        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            go
            gopls

            # The patched generator (`-g wso2-mcp`), which puts every operation
            # in one tool table.
            openapi-generator-wso2-mcp

            # And upstream's, unpatched, for looking at what stock `-g go`
            # does with the same document. The npm openapi-generator-cli is the
            # same jar fetched at runtime, which a flake cannot pin, so this is
            # the packaged one instead.
            openapi-generator-cli

            # bin/merge-specs and bin/annotate: four WSO2 documents, one binary.
            (python3.withPackages (ps: [ ps.pyyaml ]))
          ];

          # An MCP server is pure Go, and cgo only costs a C compiler.
          shellHook = ''
            export CGO_ENABLED=0
          '';
        };
      }));
}
