# janzeer-sdk-kotlin

[20 JNZ] Java example: watch new blocks

## Overview

The SDK is meant to be used from plain Java too, but the only Java example is the transfer quickstart (`examples/src/main/java/org/janzeer/examples/JavaQuickstart.java`). Subscriptions from Java need a detail that trips people: the callback is a Kotlin function type, so the lambda must return `Unit.INSTANCE`.

**What to do:** `examples/src/main/java/org/janzeer/examples/JavaWatchNewBlocks.java`, about 35 lines, the Java twin of `WatchNewBlocks.kt`:

- `JanzeerRpcWs.connect(url)` with `JANZEER_WS

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
