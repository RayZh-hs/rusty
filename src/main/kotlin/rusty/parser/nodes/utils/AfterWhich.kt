package rusty.parser.nodes.utils

fun <Owner, T> Owner.afterWhich(
    predicate: () -> T
): Owner {
    val owner = this
    predicate()
    return owner
}