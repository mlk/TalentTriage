package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * The address a reply goes to.
 *
 * A mail states the address of the sender in its `From` header, with the name
 * of the sender in front of it: `Jane Doe <jane@example.com>`. The address of
 * the reply is the address alone: the name of the sender is not part of it.
 *
 * A mail that names another address in its `Reply-To` header is answered at
 * that address. A platform that sends the mail of a recruiter writes from an
 * address that nobody reads, the `Reply-To` header names the address the
 * recruiter reads.
 *
 * @param from the value of the `From` header of the mail.
 * @param replyTo the value of the `Reply-To` header of the mail, empty when
 *   the mail has none.
 * @return the address the reply goes to.
 */
fun replyAddress(
    from: String,
    replyTo: String,
): String = address(replyTo.ifBlank { from })

/**
 * The address of a header of a mail.
 *
 * The header names the address between `<` and `>`. A header that names it
 * without them carries the address alone. A header that names no address at
 * all - a name without an address - carries no address: there is nobody to
 * write to.
 *
 * @param header the value of the header.
 * @return the address of the header, empty when the header names none.
 */
private fun address(header: String): String {
    val start = header.indexOf('<')
    val named =
        if (start >= 0) {
            val end = header.indexOf('>', startIndex = start)
            if (end > start) {
                header.substring(start + 1, end).trim()
            } else {
                ""
            }
        } else {
            header.substringBefore(',').trim()
        }
    return if (named.contains('@')) named else ""
}
