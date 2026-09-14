# Contributing

Build with JDK 17 (a toolchain is resolved automatically via foojay; on this workspace prefix commands with the
sdkman JDK 17 as `CLAUDE.md` describes).

```bash
./gradlew build                                        # compile, unit + conformance tests, apiCheck
./gradlew apiDump                                      # after an intentional public-API change (commit the *.api files)
./gradlew dokkaGenerate                                # build/dokka/html
./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false
```

## End-to-end tests and examples

They need a running Janzeer network with the faucet-funded test wallet. With the private node repo checked out
next to the conformance kit:

```bash
../sdk_conformance/e2e/node-up.sh
eval "$(../sdk_conformance/e2e/node-up.sh --env)"
./gradlew :janzeer-sdk-client:e2eTest -Djanzeer.node.url=$JANZEER_NODE_URL -Djanzeer.rpc.url=$JANZEER_RPC_URL -Djanzeer.ws.url=$JANZEER_WS_URL
./gradlew :examples:run -PmainClass=org.janzeer.examples.QuickstartTransferKt   # or any class in examples/
../sdk_conformance/e2e/node-down.sh
```

The e2e task also reads `JANZEER_NODE_URL` / `JANZEER_RPC_URL` / `JANZEER_WS_URL` / `JANZEER_E2E_MNEMONIC` /
`JANZEER_E2E_RECIPIENT` from the environment.

## Conformance vectors

`core/src/test/resources/vectors/` is a vendored copy of `sdk_conformance/vectors/`. Never edit it by hand: after a
node change run `sdk_conformance/sync.sh --regen`; `VectorsTest` verifies the copy against `SHA256SUMS`.

## Style

- `explicitApi()` is on: every public declaration is deliberate and documented.
- Amounts are `String` / `BigDecimal` at the API boundary; never `double` arithmetic on coin values.
- Java callers matter: `@JvmStatic`, `@JvmOverloads`, no `suspend` in the core surface (extensions only).
- Every new node method gets a typed wrapper (`RpcMethods` / `JanzeerClient`), a model, a MockWebServer test, an
  `apiDump`, and a line in `CHANGELOG.md`.
