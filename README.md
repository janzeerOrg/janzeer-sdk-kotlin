# janzeer-sdk-kotlin

[50 JNZ] Coroutine Flow variants of the two WebSocket subscriptions

## Overview

`JanzeerRpcWs` delivers subscription events through callbacks only: `subscribeNewBlocks(params) { block -> … }` and `subscribeAddressActivity(addresses) { event -> … }`, each returning a `Subscription`. Kotlin and Android code usually wants a `Flow`, and today everyone has to write the same `callbackFlow` wrapper.

**What to do:** in `client/src/main/kotlin/org/janzeer/sdk/rpc/`, add

- `fun JanzeerRpcWs.newBlocksFlow(params: NewBlocksParams = NewBlocksParams()): Flow<…>`
- `fun JanzeerRpcWs.add

## Installation

```bash
# Clone the repository
git clone https://github.com/janzeerOrg/janzeer-sdk-kotlin.git
cd janzeer-sdk-kotlin

# Install dependencies (if applicable)
pip install -r requirements.txt  # Python
# or
npm install  # Node.js
```

## Usage

```bash
# Run the project
python main.py  # Python
# or
node index.js  # Node.js
```

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## License

See [LICENSE](LICENSE) for details.
