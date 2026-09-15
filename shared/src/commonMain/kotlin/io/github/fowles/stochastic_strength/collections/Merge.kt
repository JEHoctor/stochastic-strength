package io.github.fowles.stochastic_strength.collections

/**
 * Explicit-import stand-in for `java.util.Map.merge`, which common code does not have.
 * On the JVM the member always wins, so Android behavior is unchanged; this resolves in
 * common and native compilations. Null-removing remaps are not supported (not used).
 */
fun <K, V : Any> MutableMap<K, V>.merge(key: K, value: V, remap: (V, V) -> V): V {
    val merged = this[key]?.let { remap(it, value) } ?: value
    this[key] = merged
    return merged
}
