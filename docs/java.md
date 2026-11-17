(Adicionar ao final do arquivo)

### Subscriptions
When using Kotlin functional types (like WebSocket callbacks) from Java, you must return `Unit.INSTANCE` at the end of your lambda:

```java
ws.subscribeNewBlocks(params, block -> {
    // your logic
    return Unit.INSTANCE; 
});
```
See `examples/src/main/java/org/janzeer/examples/JavaWatchNewBlocks.java` for a full implementation.
