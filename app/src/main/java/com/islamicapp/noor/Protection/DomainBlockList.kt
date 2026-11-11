package com.islamicapp.noor.Protection

/**
 * Starter blocklist. Replace or extend the list with a maintained, legally
 * usable hostname feed (see README) - the filter matches hosts and all of their
 * subdomains.
 */
object DomainBlockList {

    private val hosts = setOf(
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "redtube.com",
        "youporn.com",
        "onlyfans.com",
        "chaturbate.com",
        "livejasmin.com",
        "brazzers.com",
        "xhamster.com"
    )

    fun isBlocked(host: String): Boolean {
        val normalized = host.lowercase().removeSuffix(".")
        return hosts.any { normalized == it || normalized.endsWith("." + it) }
    }
}
